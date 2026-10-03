package call

import (
	"errors"
	"sync"
	"time"

	"github.com/google/uuid"
)

var validTransitions = map[Status][]Status{
	StatusRinging:    {StatusConnecting, StatusDeclined, StatusCancelled, StatusMissed},
	StatusConnecting: {StatusConnected, StatusEnded, StatusCancelled},
	StatusConnected:  {StatusEnded},
	StatusEnded:      {},
	StatusMissed:     {},
	StatusDeclined:   {},
	StatusCancelled:  {},
}

func CanTransition(from, to Status) bool {
	for _, allowed := range validTransitions[from] {
		if allowed == to {
			return true
		}
	}
	return false
}

type ActiveCall struct {
	ID        string
	CallerID  string
	CalleeID  string
	Status    Status
	StartedAt time.Time
}

type Registry struct {
	mu     sync.RWMutex
	calls  map[string]*ActiveCall
	byUser map[string]string
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

func (r *Registry) EndAllForUser(userID string) []*ActiveCall {
	r.mu.Lock()
	defer r.mu.Unlock()

	callID, ok := r.byUser[userID]
	if !ok {
		return nil
	}
	c, ok := r.calls[callID]
	if !ok {
		delete(r.byUser, userID)
		return nil
	}

	delete(r.calls, c.ID)
	delete(r.byUser, c.CallerID)
	delete(r.byUser, c.CalleeID)

	return []*ActiveCall{c}
}

func isTerminal(s Status) bool {
	switch s {
	case StatusEnded, StatusMissed, StatusDeclined, StatusCancelled:
		return true
	default:
		return false
	}
}
