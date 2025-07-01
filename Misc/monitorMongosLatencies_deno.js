// Import the MongoDB driver using ES modules syntax for Deno
import { MongoClient } from "npm:mongodb";

// --- Configuration ---
// Replace with your MongoDB connection string.
const uri = "mongodb+srv://franksnow:giraffes@latencies.25orp.mongodb.net/";
// --- End Configuration ---

// Create a new MongoClient
const client = new MongoClient(uri);

console.log("Attempting to connect to MongoDB...");
console.log("Listening for server heartbeat events to report RTT...");
console.log("RTT values represent the driver's measured latency for monitoring commands.");
console.log("Press Ctrl+C to exit.");

// Listen for successful heartbeat events
client.on("serverHeartbeatSucceeded", (event) => {
    const serverAddress = event.connectionId;
    const rtt = event.duration;

    console.log(`RTT to ${serverAddress}: ${rtt} ms`);
});

// Optional: Listen for heartbeat failures for diagnostics
client.on("serverHeartbeatFailed", (event) => {
    const serverAddress = event.connectionId;
    console.error(`Heartbeat failed to ${serverAddress}: ${event.failure}`);
});

async function run() {
    try {
        // Connect the client to the server
        await client.connect();
        console.log("Successfully connected to MongoDB cluster. Monitoring active.");

        // Keep the script running indefinitely to listen for events
        await new Promise(resolve => setTimeout(resolve, Infinity));

    } catch (err) {
        console.error("Connection or monitoring setup error:", err);
    } finally {
        console.log("\nClosing MongoDB connection...");
        await client.close();
        console.log("Connection closed.");
    }
}

run().catch(console.error);

// Handle Ctrl+C gracefully
Deno.addSignalListener("SIGINT", async () => {
    console.log("\nCaught interrupt signal (Ctrl+C)");
    await client.close();
    console.log("MongoDB connection closed gracefully.");
    Deno.exit(0);
});
