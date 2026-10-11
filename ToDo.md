# Terminal Messenger — Task Tracker

## Phase 1 — Project foundation
Get a Java application running.
- [x] Create a Git repository
- [ ] Set up Gradle and the Java source structure
- [ ] Create ChatServer.java
- [ ] Create ChatClient.java
- [ ] Verify both applications run

## Phase 2 — Networking
Get two terminals communicating.
- [ ] Open a ServerSocket
- [ ] Accept a client Socket
- [ ] Read and send text
- [ ] Run a receiver thread on the client
- [ ] Test two-way messaging with two clients

## Phase 3 — User management
Identify each connected client.
- [ ] Create ClientHandler.java
- [ ] Ask each client for a username
- [ ] Reject duplicate usernames
- [ ] Track online users in a ConcurrentHashMap
- [ ] Remove users when they disconnect

## Phase 4 — Chat rooms
Deliver messages to the correct group.
- [ ] Create ChatRoom.java
- [ ] Add room membership tracking
- [ ] Implement /join and /leave
- [ ] Implement /rooms
- [ ] Broadcast messages to room members
- [ ] Test that rooms remain isolated

## Phase 5 — Private messages
Send messages to one recipient.
- [ ] Implement /users
- [ ] Implement /msg username message
- [ ] Look up the recipient on the server
- [ ] Deliver messages only to the intended user
- [ ] Handle unknown or offline recipients

## Phase 6 — Reliability
Make normal failures safe.
- [ ] Handle unexpected disconnects
- [ ] Validate commands and message sizes
- [ ] Prevent concurrent writes from interleaving
- [ ] Test simultaneous messages
- [ ] Test invalid input and cleanup

## Phase 7 — Polish
Make the messenger pleasant to use.
- [ ] Add timestamps and terminal colors
- [ ] Improve /help
- [ ] Add automated tests
- [ ] Write setup instructions in README.md
- [ ] Try connections from two computers
