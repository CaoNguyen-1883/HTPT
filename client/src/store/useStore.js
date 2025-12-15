import { create } from "zustand";

const useStore = create((set, get) => ({
    // State
    nodes: [],
    migrations: [],
    codePackages: [],
    logs: [],
    connected: false,

    executionResults: [],

    // Actions
    setConnected: (connected) => set({ connected }),

    setNodes: (nodes) => set({ nodes }),

    addNode: (node) =>
        set((state) => ({
            nodes: [...state.nodes.filter((n) => n.id !== node.id), node],
        })),

    removeNode: (nodeId) =>
        set((state) => ({
            nodes: state.nodes.filter((n) => n.id !== nodeId),
        })),

    updateNodeMetrics: (nodeId, metrics) =>
        set((state) => ({
            nodes: state.nodes.map((n) =>
                n.id === nodeId ? { ...n, metrics } : n,
            ),
        })),

    updateNodeStatus: (nodeId, status) =>
        set((state) => ({
            nodes: state.nodes.map((n) =>
                n.id === nodeId ? { ...n, status } : n,
            ),
        })),

    addMigration: (migration) =>
        set((state) => ({
            migrations: [...state.migrations, migration],
        })),

    updateMigration: (migration) =>
        set((state) => ({
            migrations: state.migrations.map((m) =>
                m.id === migration.id ? migration : m,
            ),
        })),

    addCodePackage: (codePackage) =>
        set((state) => ({
            codePackages: [...state.codePackages, codePackage],
        })),

    addLog: (message, type = "info", metadata = {}) =>
        set((state) => ({
            logs: [
                ...state.logs,
                {
                    message,
                    type,
                    timestamp: Date.now(),
                    ...metadata,
                },
            ].slice(-100), // Keep last 100 logs
        })),

    clearLogs: () => set({ logs: [] }),

    addExecutionResult: (result) =>
        set((state) => ({
            executionResults: [...state.executionResults, result].slice(-20),
        })),

    clearExecutionResults: () => set({ executionResults: [] }),

    // Selectors
    getActiveMigration: () => {
        const state = get();
        return state.migrations.find((m) => m.status === "IN_PROGRESS");
    },

    getNodeById: (nodeId) => {
        const state = get();
        return state.nodes.find((n) => n.id === nodeId);
    },
}));

export default useStore;
