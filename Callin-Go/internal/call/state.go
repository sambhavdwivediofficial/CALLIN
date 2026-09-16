package call

import (
	"errors"
	"sync"
	"time"

	"github.com/google/uuid"
)

// validTransitions defines every allowed status change. Any
// transition not listed here is rejected — this is what stops the
// system from, for example, "accepting" a call that has already ended.
var validTransitions = map[Status][]Status{
	StatusRinging:    {StatusConnecting, StatusDeclined, StatusCancelled, StatusMissed},
	StatusConnecting: {StatusConnected, StatusEnded, StatusCancelled},
	StatusConnected:  {StatusEnded},
	StatusEnded:      {},
	StatusMissed:     {},
	StatusDeclined:   {},
	StatusCancelled:  {},
}

// CanTransition reports whether moving from `from` to `to` is a
// legal call-state transition.
func CanTransition(from, to Status) bool {
	for _, allowed := range validTransitions[from] {
		if allowed == to {
			return true
		}
	}
	return false
}

// ActiveCall is the live, in-memory record of a call that is
// currently ringing or connected. Terminal calls are written to
// PostgreSQL and dropped from memory.
type ActiveCall struct {
	ID        string
	CallerID  string
	CalleeID  string
	Status    Status
	StartedAt time.Time
}

// Registry tracks every currently active call in memory. A single
// backend instance is assumed (see middleware.IPRateLimiter for the
// same tradeoff) — this keeps the system simple while call volume
// is low, and is the natural place to introduce Redis later if
// CALLIN ever needs multiple backend replicas.
type Registry struct {
	mu     sync.RWMutex
	calls  map[string]*ActiveCall
	byUser map[string]string // userID -> callID, so we can reject "already on a call"
}

func NewRegistry() *Registry {
	return &Registry{
		calls:  make(map[string]*ActiveCall),
		byUser: make(map[string]string),
	}
}

var (
	ErrUserBusy          = errors.New("call: user is already on a call")
	ErrCallNotFound      = errors.New("call: not found")
	ErrInvalidTransition = errors.New("call: invalid state transition")
)

// Start creates a new ringing call between caller and callee. It
// fails if either party is already on a call.
func (r *Registry) Start(callerID, calleeID string) (*ActiveCall, error) {
	r.mu.Lock()
	defer r.mu.Unlock()

	if _, busy := r.byUser[callerID]; busy {
		return nil, ErrUserBusy
	}
	if _, busy := r.byUser[calleeID]; busy {
		return nil, ErrUserBusy
	}

	c := &ActiveCall{
		ID:        uuid.NewString(),
		CallerID:  callerID,
		CalleeID:  calleeID,
		Status:    StatusRinging,
		StartedAt: time.Now(),
	}

	r.calls[c.ID] = c
	r.byUser[callerID] = c.ID
	r.byUser[calleeID] = c.ID

	return c, nil
}

// Transition moves a call to a new status, validating that the
// transition is legal. Reaching a terminal status removes the call
// from the active registry.
func (r *Registry) Transition(callID string, to Status) (*ActiveCall, error) {
	r.mu.Lock()
	defer r.mu.Unlock()

	c, ok := r.calls[callID]
	if !ok {
		return nil, ErrCallNotFound
	}

	if !CanTransition(c.Status, to) {
		return nil, ErrInvalidTransition
	}

	c.Status = to

	if isTerminal(to) {
		delete(r.calls, c.ID)
		delete(r.byUser, c.CallerID)
		delete(r.byUser, c.CalleeID)
	}

	return c, nil
}

func (r *Registry) Get(callID string) (*ActiveCall, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	c, ok := r.calls[callID]
	return c, ok
}

// ActiveCallForUser returns the call a user is currently part of, if any.
func (r *Registry) ActiveCallForUser(userID string) (*ActiveCall, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	callID, ok := r.byUser[userID]
	if !ok {
		return nil, false
	}
	c := r.calls[callID]
	return c, c != nil
}

func isTerminal(s Status) bool {
	switch s {
	case StatusEnded, StatusMissed, StatusDeclined, StatusCancelled:
		return true
	default:
		return false
	}
}
