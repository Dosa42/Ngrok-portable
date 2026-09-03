# java-sdk-example

This example application starts a hello world HTTP server on port 8085 and then uses the [ngrok Java SDK](https://github.com/ngrok/ngrok-java) (`com.ngrok:ngrok-java`) to forward public traffic to that server. See the [SDK reference](https://ngrok.github.io/ngrok-java/) for more details. When you run it, you'll get a public URL that anyone can use to access your app.

## Clone and Run This Example

```sh
git clone git@github.com:ngrok/java-sdk-example.git
cd java-sdk-example
NGROK_AUTHTOKEN=<token> mvn compile exec:java
```

## License

MIT
