import { useState } from "react";
import NetworkTopology from "./NetworkTopology";
import NodeList from "./NodeList";
import MigrationPanel from "./MigrationPanel";
import ControlPanel from "./ControlPanel";
import LogViewer from "./LogViewer";
import MigrationHistory from "./MigrationHistory";
import NodeMetrics from "./NodeMetrics";
import BatchMigration from "./BatchMigration";
import DatabaseViewer from "./DatabaseViewer";
import useStore from "../store/useStore";

function Dashboard() {
    const { nodes, migrations, logs } = useStore();
    const [selectedNode, setSelectedNode] = useState(null);
    const [activeView, setActiveView] = useState("main"); // main, advanced, database

    return (
        <div className="dashboard">
            {/* View Switcher */}
            <div className="view-switcher">
                <button
                    className={`view-btn ${activeView === "main" ? "active" : ""}`}
                    onClick={() => setActiveView("main")}
                >
                    Main Dashboard
                </button>
                <button
                    className={`view-btn ${activeView === "advanced" ? "active" : ""}`}
                    onClick={() => setActiveView("advanced")}
                >
                    Advanced Controls
                </button>
                <button
                    className={`view-btn ${activeView === "database" ? "active" : ""}`}
                    onClick={() => setActiveView("database")}
                >
                    Database Viewer
                </button>
            </div>

            {/* Main View */}
            {activeView === "main" && (
                <div className="dashboard-layout main-view">
                    <div className="dashboard-left">
                        <NetworkTopology
                            nodes={nodes}
                            selectedNode={selectedNode}
                            onNodeSelect={setSelectedNode}
                        />
                        <NodeList
                            nodes={nodes}
                            selectedNode={selectedNode}
                            onNodeSelect={setSelectedNode}
                        />
                        <NodeMetrics />
                    </div>

                    <div className="dashboard-right">
                        <ControlPanel nodes={nodes} />
                        <MigrationPanel migrations={migrations} nodes={nodes} />
                        <LogViewer logs={logs} />
                    </div>
                </div>
            )}

            {/* Advanced View */}
            {activeView === "advanced" && (
                <div className="dashboard-layout advanced-view">
                    <div className="advanced-left">
                        <BatchMigration />
                        <MigrationHistory />
                    </div>

                    <div className="advanced-right">
                        <NodeMetrics />
                        <LogViewer logs={logs} />
                    </div>
                </div>
            )}

            {/* Database View */}
            {activeView === "database" && (
                <div className="dashboard-layout database-view">
                    <DatabaseViewer />
                </div>
            )}
        </div>
    );
}

export default Dashboard;
