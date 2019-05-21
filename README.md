# Java FTP Server And Client

## by Daniel Booher

This is a simple FTP client and server written in `Java` for a school project that I though I should share

# Server Usage: 

`java Server <port>`

> `port` can be any open valid port or 0 for the first open available port
> The server remains running until it is manually killed with ^C (Ctrl+C)
> If 0 is used for the port number, then after closing connections with a client a new port is used (the old port is still closed)
> This program pulls files from a directory named `AvailableFiles` within the same directory that this class file is in (`FTP/Server/AvailableFiles` by default)

# Server Example:

Input: `java Server 5000`

# Client Usage: 

`java Client <IP Address> <port>`

`java Client <Host> <port>`

`IP Address` must be a valid IPv4 Address
`Host` must be a valid evaluatable Hostname
`port` can be any open valid port
The Client downloads files and saves them into a directory named `DownloadedFiles` that is within the same directory that this class file is in (`FTP/Client/DownloadedFiles` by default)

# Client Example:

Input: `java Client 127.0.0.1 5000`

Input: `java Client localhost 1414`