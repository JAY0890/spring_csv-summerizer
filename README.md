# CSV Summarizer & AI RAG Agent 🚀

A high-performance, full-stack application that leverages Spring Boot 4, Langchain4j, and the Google Gemini AI API to allow you to instantly "chat" with your CSV data.

## Features ✨

* **Premium Vanilla JS Frontend**: A visually stunning UI featuring glassmorphism, glowing orb animations, and smooth drag-and-drop file uploads built without any bulky JavaScript frameworks.
* **Intelligent CSV RAG (Retrieval-Augmented Generation)**: 
  * Parses CSV files on upload.
  * Combines data into efficient chunks (10 rows per chunk) to maximize context logic and drastically reduce API calls.
  * Embeds text segments using the latest `gemini-embedding-2` model.
  * Queries and retrieves relevant data directly from an in-memory vector database.
* **Ultra-Fast Virtual Threads**: Employs Java 25 Virtual Threads to perform lightning-fast concurrent HTTP requests to the Gemini Embedding API, effectively bypassing sequential latency.
* **Robust Auto-Retries**: Safely handles Free Tier rate limits (HTTP 429) automatically by sleeping and resuming.

---

## 🏗️ Architecture Stack
* **Backend**: Java 25, Spring Boot 4.0.6, Langchain4j 1.14.0
* **Frontend**: Vanilla JavaScript, HTML5, Modern CSS Variables
* **AI Provider**: Google Generative AI (Gemini Flash Lite Latest, Gemini Embedding 2)

---

## 🚀 How to Run Locally

If you want to run the project locally via Maven, ensure you have an active Gemini API key.

1. **Set your API Key** in your terminal:
   ```bash
   export GOOGLEAI="your_api_key_here"
   ```
2. **Start the backend**:
   ```bash
   ./mvnw clean spring-boot:run 
   ```
3. **Open the frontend**:
   Simply open the `frontend/index.html` file in any web browser!

---

## 🐳 How to Run with Docker

You can completely containerize the application to run it anywhere without needing Java 25 or Maven installed locally.

### 1. Build the Docker Image
From the root directory of the project, run:
```bash
docker build -t csv-summarizer:latest .
```

### 2. Run the Container
You MUST pass your `GOOGLEAI` environment variable into the container so it can communicate with Google's servers. 

Run the following command (replace `YOUR_API_KEY` with your actual key):
```bash
docker run -p 8080:8080 -e GOOGLEAI="YOUR_API_KEY" csv-summarizer:latest
```


### 3. Use the App
Once the container boots and says `Started CsvsummerizerApplication`:
1. Open the `/frontend/index.html` file in your web browser.
2. Drag and drop your CSV file.
3. Start asking questions!
