import axios from "axios";

const API_URL = "http://localhost:8080/api";

const api = axios.create({
    baseURL: API_URL,
    headers: {
        "Content-Type": "application/json",
    },
});

// Nodes
export async function getNodes() {
    const response = await api.get("/nodes");
    return response.data;
}

export async function getTopology() {
    const response = await api.get("/nodes/topology");
    return response.data;
}

export async function getNodeMetrics(nodeId) {
    const response = await api.get(`/nodes/${nodeId}/metrics`);
    return response.data;
}

// Migrations
export async function initiateMigration(request) {
    const response = await api.post("/migrations", request);
    return response.data;
}

export async function getMigration(id) {
    const response = await api.get(`/migrations/${id}`);
    return response.data;
}

export async function getAllMigrations() {
    const response = await api.get("/migrations");
    return response.data;
}

export async function cancelMigration(id) {
    const response = await api.post(`/migrations/${id}/cancel`);
    return response.data;
}

// Code
export async function uploadCode(codeData) {
    const response = await api.post("/code", codeData);
    return response.data;
}

export async function getCode(id) {
    const response = await api.get(`/code/${id}`);
    return response.data;
}

export async function getAllCode() {
    const response = await api.get("/code");
    return response.data;
}

export default api;
