package com.example

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

class RabiyaHttpServer(
    private val context: Context,
    private val port: Int,
    private val viewModel: MainViewModel
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        thread(name = "RabiyaHttpServerThread") {
            try {
                serverSocket = ServerSocket(port)
                Log.d("RabiyaHttpServer", "Server started on port $port")
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    thread { handleConnection(socket) }
                }
            } catch (e: Exception) {
                Log.e("RabiyaHttpServer", "Server error", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("RabiyaHttpServer", "Error closing server", e)
        }
        serverSocket = null
    }

    private fun handleConnection(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = BufferedOutputStream(socket.getOutputStream())

            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]

            // Read headers to find Content-Length for POST request
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
                if (line!!.lowercase().startsWith("content-length:")) {
                    contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                }
            }

            if (method == "GET") {
                if (path == "/") {
                    serveHtmlIndex(out)
                } else if (path.startsWith("/download")) {
                    serveFileDownload(path, out)
                } else if (path.startsWith("/delete")) {
                    serveFileDelete(path, out)
                } else {
                    serve404(out)
                }
            } else if (method == "POST" && path.startsWith("/upload")) {
                handleFileUpload(path, contentLength, socket.getInputStream(), out)
            } else {
                serve404(out)
            }

            out.flush()
            socket.close()
        } catch (e: Exception) {
            Log.e("RabiyaHttpServer", "Error handling socket request", e)
        }
    }

    private fun serveHtmlIndex(out: BufferedOutputStream) {
        val files = viewModel.roboFiles.value
        val foldersGrouped = files.groupBy { it.type }

        val rootDir = File(context.filesDir, "FileManager")
        val internalDir = File(rootDir, "InternalStorage")
        val customFolders = internalDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        val allFolders = (listOf("Images", "Voice", "Video", "PDFs", "Documents") + customFolders.map { "InternalStorage/$it" }).distinct()

        val html = StringBuilder()
        html.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Rabiya AI - Cyber File Desk</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background: #050814;
                        color: #f1f1f1;
                        margin: 0;
                        padding: 0;
                    }
                    header {
                        background: linear-gradient(135deg, #111728, #050814);
                        border-bottom: 2px solid #00f3ff;
                        padding: 24px;
                        text-align: center;
                        box-shadow: 0 4px 20px rgba(0, 243, 255, 0.15);
                    }
                    h1 {
                        margin: 0;
                        font-size: 26px;
                        color: #00f3ff;
                        text-shadow: 0 0 10px rgba(0, 243, 255, 0.5);
                        letter-spacing: 1px;
                    }
                    .subtitle {
                        color: #ff007f;
                        font-size: 13px;
                        font-weight: bold;
                        text-transform: uppercase;
                        margin-top: 6px;
                        letter-spacing: 1.5px;
                    }
                    .container {
                        max-width: 900px;
                        margin: 40px auto;
                        padding: 0 20px;
                    }
                    .panel {
                        background: #0f1524;
                        border: 1px solid #1e293b;
                        border-radius: 16px;
                        padding: 24px;
                        margin-bottom: 30px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                    }
                    .panel-title {
                        font-size: 18px;
                        font-weight: bold;
                        color: #00f3ff;
                        margin-bottom: 16px;
                        border-bottom: 1px dashed #1e293b;
                        padding-bottom: 8px;
                    }
                    .form-group {
                        margin-bottom: 15px;
                    }
                    label {
                        display: block;
                        font-size: 12px;
                        font-weight: bold;
                        color: #94a3b8;
                        margin-bottom: 6px;
                    }
                    select, input[type="file"], button {
                        width: 100%;
                        background: #111728;
                        border: 1.5px solid #2d3748;
                        color: #fff;
                        padding: 12px;
                        font-size: 14px;
                        border-radius: 8px;
                        box-sizing: border-box;
                        outline: none;
                        transition: all 0.2s ease;
                    }
                    select:focus, input[type="file"]:focus {
                        border-color: #00f3ff;
                        box-shadow: 0 0 8px rgba(0,243,255,0.2);
                    }
                    button {
                        background: linear-gradient(135deg, #00f3ff, #ff007f);
                        border: none;
                        font-weight: bold;
                        cursor: pointer;
                        color: #000;
                        margin-top: 10px;
                    }
                    button:hover {
                        opacity: 0.9;
                        box-shadow: 0 0 15px rgba(255,0,127,0.4);
                    }
                    .folder-group {
                        margin-bottom: 25px;
                    }
                    .folder-header {
                        font-weight: bold;
                        font-size: 14px;
                        color: #ff007f;
                        text-transform: uppercase;
                        background: rgba(255, 0, 127, 0.08);
                        padding: 8px 12px;
                        border-radius: 6px;
                        border-left: 3px solid #ff007f;
                        margin-bottom: 10px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 13px;
                    }
                    th {
                        text-align: left;
                        padding: 8px 12px;
                        color: #64748b;
                        border-bottom: 1px solid #1e293b;
                    }
                    td {
                        padding: 10px 12px;
                        border-bottom: 1px solid #0f1524;
                        color: #cbd5e1;
                    }
                    tr:hover td {
                        background: #162035;
                    }
                    .btn-action {
                        color: #00f3ff;
                        text-decoration: none;
                        font-weight: bold;
                        margin-right: 12px;
                    }
                    .btn-action:hover {
                        text-decoration: underline;
                    }
                    .btn-delete {
                        color: #ff3366;
                    }
                    footer {
                        text-align: center;
                        font-size: 11px;
                        color: #475569;
                        padding: 20px 0;
                    }
                    #statusMsg {
                        margin-top: 12px;
                        padding: 10px;
                        border-radius: 6px;
                        display: none;
                        font-size: 12px;
                    }
                    .status-success {
                        background: rgba(16, 185, 129, 0.15);
                        border: 1px solid #10b981;
                        color: #10b981;
                    }
                    .status-error {
                        background: rgba(244, 63, 94, 0.15);
                        border: 1px solid #f43f5e;
                        color: #f43f5e;
                    }
                </style>
            </head>
            <body>
                <header>
                    <h1>🪐 RABIYA CYBER FILE DESK</h1>
                    <div class="subtitle">Wireless & USB Computer Exchange Hub</div>
                </header>
                <div class="container">
                    
                    <!-- Upload Panel -->
                    <div class="panel">
                        <div class="panel-title">📤 UPLINK NEW FILE TO PHONE</div>
                        <div class="form-group">
                            <label for="folderSelect">TARGET DIRECTORY / CATEGORY</label>
                            <select id="folderSelect">
                                ${allFolders.joinToString("") { "<option value=\"$it\">$it</option>" }}
                            </select>
                        </div>
                        <div class="form-group">
                            <label for="fileInput">SELECT FILE FROM COMPUTER / LAPTOP</label>
                            <input type="file" id="fileInput">
                        </div>
                        <button onclick="uploadFile()">START PC-TO-APP TRANSPORT</button>
                        <div id="statusMsg"></div>
                    </div>

                    <!-- Files Explorer Panel -->
                    <div class="panel">
                        <div class="panel-title">📁 ACTIVE DIRECTORY SCHEMAS</div>
        """.trimIndent())

        if (files.isEmpty()) {
            html.append("<p style='text-align:center; color:#64748b;'>No files present inside Rabiya File Core. Please write notes or complete transactions first.</p>")
        } else {
            foldersGrouped.forEach { (cat, catFiles) ->
                val folderDisplayName = if (cat.startsWith("InternalStorage/")) {
                    "📂 PHONE INTERNAL STORAGE - ${cat.substringAfter("/")}"
                } else {
                    "🪐 SYSTEM STORAGE - $cat"
                }
                html.append("""
                    <div class="folder-group">
                        <div class="folder-header">$folderDisplayName (${catFiles.size})</div>
                        <table>
                            <thead>
                                <tr>
                                    <th>FILENAME</th>
                                    <th>SIZE</th>
                                    <th>ACTIONS</th>
                                </tr>
                            </thead>
                            <tbody>
                """.trimIndent())

                catFiles.forEach { file ->
                    val downloadUrl = "/download?path=" + java.net.URLEncoder.encode(file.absolutePath, "UTF-8")
                    val deleteUrl = "/delete?path=" + java.net.URLEncoder.encode(file.absolutePath, "UTF-8")
                    html.append("""
                        <tr>
                            <td style="font-weight:600;">${file.name}</td>
                            <td style="color:#94a3b8;">${file.size}</td>
                            <td>
                                <a class="btn-action" href="$downloadUrl" target="_blank">📥 DOWNLOAD</a>
                                <a class="btn-action btn-delete" href="$deleteUrl" onclick="return confirm('Ensure deletion of ${file.name}?');">❌ DELETE</a>
                            </td>
                        </tr>
                    """.trimIndent())
                }

                html.append("""
                            </tbody>
                        </table>
                    </div>
                """.trimIndent())
            }
        }

        html.append("""
                    </div>
                </div>
                
                <footer>
                    Power-coupled with Rabiya Core v3.5 AI Client Engine &bull; System Time: 2026-06-05
                </footer>

                <script>
                    function uploadFile() {
                        const fileInput = document.getElementById('fileInput');
                        const folderSelect = document.getElementById('folderSelect');
                        const statusMsg = document.getElementById('statusMsg');
                        
                        if (fileInput.files.length === 0) {
                            showStatus('Please choose a file to transport.', false);
                            return;
                        }
                        
                        const file = fileInput.files[0];
                        const targetFolder = folderSelect.value;
                        
                        showStatus('Transmitting file system blocks: ' + file.name + '...', true);
                        statusMsg.className = '';
                        statusMsg.style.display = 'block';
                        statusMsg.style.background = 'rgba(0, 243, 255, 0.1)';
                        statusMsg.style.border = '1px solid #00f3ff';
                        statusMsg.style.color = '#00f3ff';

                        const uploadUrl = '/upload?filename=' + encodeURIComponent(file.name) + '&folder=' + encodeURIComponent(targetFolder);
                        
                        fetch(uploadUrl, {
                            method: 'POST',
                            body: file
                        })
                        .then(res => {
                            if (res.ok) {
                                showStatus('✅ Transmission Complete! ' + file.name + ' successfully saved inside directory (' + targetFolder + ')!', true);
                                setTimeout(() => { window.location.reload(); }, 1500);
                            } else {
                                showStatus('❌ Port Transport Failure. High impedance code returned: ' + res.status, false);
                            }
                        })
                        .catch(err => {
                            showStatus('❌ Critical Ethernet socket error: ' + err, false);
                        });
                    }
                    
                    function showStatus(text, isPending) {
                        const statusMsg = document.getElementById('statusMsg');
                        statusMsg.innerText = text;
                        statusMsg.style.display = 'block';
                        if (!isPending) {
                            statusMsg.className = text.startsWith('✅') ? 'status-success' : 'status-error';
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent())

        val bytes = html.toString().toByteArray(Charsets.UTF_8)
        try {
            out.write("HTTP/1.1 200 OK\r\n".toByteArray())
            out.write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
            out.write("Content-Length: ${bytes.size}\r\n".toByteArray())
            out.write("\r\n".toByteArray())
            out.write(bytes)
        } catch (e: Exception) {
            Log.e("RabiyaHttpServer", "Error sending html index", e)
        }
    }

    private fun serveFileDownload(path: String, out: BufferedOutputStream) {
        val decodedPath = try {
            val idx = path.indexOf("=")
            if (idx != -1) {
                URLDecoder.decode(path.substring(idx + 1), "UTF-8")
            } else ""
        } catch (e: Exception) { "" }

        if (decodedPath.isNotBlank()) {
            val file = File(decodedPath)
            if (file.exists() && file.isFile) {
                try {
                    val bytes = file.readBytes()
                    out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                    out.write("Content-Type: application/octet-stream\r\n".toByteArray())
                    out.write("Content-Disposition: attachment; filename=\"${file.name}\"\r\n".toByteArray())
                    out.write("Content-Length: ${bytes.size}\r\n".toByteArray())
                    out.write("\r\n".toByteArray())
                    out.write(bytes)
                    return
                } catch (e: Exception) {
                    Log.e("RabiyaHttpServer", "Error sending download bytes", e)
                }
            }
        }
        serve404(out)
    }

    private fun serveFileDelete(path: String, out: BufferedOutputStream) {
        val decodedPath = try {
            val idx = path.indexOf("=")
            if (idx != -1) {
                URLDecoder.decode(path.substring(idx + 1), "UTF-8")
            } else ""
        } catch (e: Exception) { "" }

        if (decodedPath.isNotBlank()) {
            val file = File(decodedPath)
            if (file.exists() && file.isFile) {
                file.delete()
                viewModel.refreshRoboFiles(context)
                try {
                    out.write("HTTP/1.1 302 Found\r\n".toByteArray())
                    out.write("Location: /\r\n".toByteArray())
                    out.write("\r\n".toByteArray())
                    return
                } catch (e: Exception) {
                    Log.e("RabiyaHttpServer", "Error resolving redirect", e)
                }
            }
        }
        serve404(out)
    }

    private fun handleFileUpload(path: String, contentLength: Int, inputStream: java.io.InputStream, out: BufferedOutputStream) {
        try {
            val finalPath = if (path.contains("?")) path else "/upload?$path"
            var filename = "uploaded_file_${System.currentTimeMillis()}"
            var folder = "Downloads"

            val queryIdx = finalPath.indexOf("?")
            if (queryIdx != -1) {
                val query = finalPath.substring(queryIdx + 1)
                query.split("&").forEach { pair ->
                    val entry = pair.split("=")
                    if (entry.size == 2) {
                        val key = entry[0]
                        val value = URLDecoder.decode(entry[1], "UTF-8")
                        if (key == "filename") filename = value
                        if (key == "folder") folder = value
                    }
                }
            }

            val rootDir = File(context.filesDir, "FileManager")
            val targetFolder = File(rootDir, folder)
            if (!targetFolder.exists()) {
                targetFolder.mkdirs()
            }
            val targetFile = File(targetFolder, filename)
            
            val outputStream = java.io.FileOutputStream(targetFile)
            val buffer = ByteArray(4096)
            var totalRead = 0
            while (totalRead < contentLength) {
                val toRead = if (contentLength - totalRead < 4096) contentLength - totalRead else 4096
                val bytesRead = inputStream.read(buffer, 0, toRead)
                if (bytesRead == -1) break
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
            }
            outputStream.flush()
            outputStream.close()

            viewModel.refreshRoboFiles(context)

            val resp = "Upload complete".toByteArray()
            out.write("HTTP/1.1 200 OK\r\n".toByteArray())
            out.write("Content-Type: text/plain\r\n".toByteArray())
            out.write("Content-Length: ${resp.size}\r\n".toByteArray())
            out.write("\r\n".toByteArray())
            out.write(resp)
        } catch (e: Exception) {
            Log.e("RabiyaHttpServer", "Error uploading", e)
            val resp = "Upload failed: ${e.message}".toByteArray()
            try {
                out.write("HTTP/1.1 500 Internal Server Error\r\n".toByteArray())
                out.write("Content-Type: text/plain\r\n".toByteArray())
                out.write("Content-Length: ${resp.size}\r\n".toByteArray())
                out.write("\r\n".toByteArray())
                out.write(resp)
            } catch (ignored: Exception) {}
        }
    }

    private fun serve404(out: BufferedOutputStream) {
        val resp = "404 Not Found".toByteArray()
        try {
            out.write("HTTP/1.1 404 Not Found\r\n".toByteArray())
            out.write("Content-Type: text/plain\r\n".toByteArray())
            out.write("Content-Length: ${resp.size}\r\n".toByteArray())
            out.write("\r\n".toByteArray())
            out.write(resp)
        } catch (ignored: Exception) {}
    }
}
