const API_BASE = 'http://localhost:8080/api/csv';

// DOM Elements
const uploadSection = document.getElementById('upload-section');
const chatSection = document.getElementById('chat-section');
const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('file-input');
const uploadProgress = document.getElementById('upload-progress');
const loadedFileName = document.getElementById('loaded-file-name');
const resetBtn = document.getElementById('reset-btn');
const chatHistory = document.getElementById('chat-history');
const chatForm = document.getElementById('chat-form');
const questionInput = document.getElementById('question-input');
const sendBtn = document.getElementById('send-btn');

// --- Drag & Drop Upload Logic ---

// Prevent default drag behaviors
['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
    dropZone.addEventListener(eventName, preventDefaults, false);
    document.body.addEventListener(eventName, preventDefaults, false);
});

function preventDefaults(e) {
    e.preventDefault();
    e.stopPropagation();
}

// Highlight drop zone
['dragenter', 'dragover'].forEach(eventName => {
    dropZone.addEventListener(eventName, () => dropZone.classList.add('dragover'), false);
});

['dragleave', 'drop'].forEach(eventName => {
    dropZone.addEventListener(eventName, () => dropZone.classList.remove('dragover'), false);
});

// Handle drop
dropZone.addEventListener('drop', (e) => {
    const dt = e.dataTransfer;
    const files = dt.files;
    if (files.length > 0) handleFile(files[0]);
});

// Handle click
dropZone.addEventListener('click', () => fileInput.click());
fileInput.addEventListener('change', (e) => {
    if (e.target.files.length > 0) handleFile(e.target.files[0]);
});

function handleFile(file) {
    if (!file.name.endsWith('.csv')) {
        alert('Please upload a valid CSV file.');
        return;
    }

    // Show progress loader
    dropZone.classList.add('hidden');
    uploadProgress.classList.remove('hidden');

    const formData = new FormData();
    formData.append('file', file);

    fetch(`${API_BASE}/upload`, {
        method: 'POST',
        body: formData
    })
    .then(response => {
        if (!response.ok) throw new Error('Upload failed');
        return response.json();
    })
    .then(data => {
        // Switch to chat interface
        loadedFileName.textContent = `${file.name} loaded`;
        uploadSection.classList.add('hidden');
        chatSection.classList.remove('hidden');
    })
    .catch(error => {
        console.error('Error:', error);
        alert('An error occurred during upload. Please ensure the backend is running.');
        // Reset UI
        uploadProgress.classList.add('hidden');
        dropZone.classList.remove('hidden');
    });
}

// --- Chat Logic ---

resetBtn.addEventListener('click', () => {
    chatSection.classList.add('hidden');
    uploadProgress.classList.add('hidden');
    dropZone.classList.remove('hidden');
    uploadSection.classList.remove('hidden');
    fileInput.value = '';
    
    // Clear chat history (keep first message)
    while (chatHistory.children.length > 1) {
        chatHistory.removeChild(chatHistory.lastChild);
    }
});

chatForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const question = questionInput.value.trim();
    if (!question) return;

    // Add User Message
    addMessage(question, 'user');
    questionInput.value = '';
    
    // Add Loading Indicator
    const loaderId = addTypingIndicator();
    
    // Disable input while generating
    questionInput.disabled = true;
    sendBtn.disabled = true;

    fetch(`${API_BASE}/ask`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: question })
    })
    .then(response => response.json())
    .then(data => {
        removeElement(loaderId);
        
        if (data.error) {
            addMessage(`Error: ${data.error}`, 'system');
        } else {
            addMessage(data.answer, 'system');
        }
    })
    .catch(error => {
        removeElement(loaderId);
        addMessage('Sorry, I encountered a network error connecting to the backend.', 'system');
        console.error(error);
    })
    .finally(() => {
        questionInput.disabled = false;
        sendBtn.disabled = false;
        questionInput.focus();
    });
});

function addMessage(text, sender) {
    const msgDiv = document.createElement('div');
    msgDiv.className = `message ${sender}-msg`;
    
    const avatar = document.createElement('div');
    avatar.className = `avatar ${sender === 'user' ? 'user-avatar' : 'ai-avatar'}`;
    avatar.textContent = sender === 'user' ? 'U' : 'AI';
    
    const bubble = document.createElement('div');
    bubble.className = 'msg-bubble';
    
    // Basic markdown to HTML (handle newlines and bold text)
    let formattedText = text
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\n/g, '<br>');
    
    bubble.innerHTML = formattedText;
    
    msgDiv.appendChild(avatar);
    msgDiv.appendChild(bubble);
    chatHistory.appendChild(msgDiv);
    
    // Scroll to bottom
    chatHistory.scrollTop = chatHistory.scrollHeight;
}

function addTypingIndicator() {
    const id = 'loader-' + Date.now();
    const msgDiv = document.createElement('div');
    msgDiv.className = `message system-msg`;
    msgDiv.id = id;
    
    const avatar = document.createElement('div');
    avatar.className = `avatar ai-avatar`;
    avatar.textContent = 'AI';
    
    const bubble = document.createElement('div');
    bubble.className = 'msg-bubble typing-indicator';
    bubble.innerHTML = `<div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div>`;
    
    msgDiv.appendChild(avatar);
    msgDiv.appendChild(bubble);
    chatHistory.appendChild(msgDiv);
    chatHistory.scrollTop = chatHistory.scrollHeight;
    
    return id;
}

function removeElement(id) {
    const el = document.getElementById(id);
    if (el) el.remove();
}
