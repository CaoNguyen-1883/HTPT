import { useState } from "react";
import { uploadCode } from "../services/api";
import useStore from "../store/useStore";

function ControlPanel({ nodes }) {
    const [code, setCode] = useState(`// Mobile Agent Example
def data = []
def nodeId = nodeId

def collect() {
    data << [
        node: nodeId,
        time: System.currentTimeMillis(),
        value: Math.random() * 100
    ]
    return "Collected from " + nodeId
}

collect()
`);

    const [codeName, setCodeName] = useState("MobileAgent");
    const [initialNode, setInitialNode] = useState("");
    const [loading, setLoading] = useState(false);

    const { addLog, addCodePackage } = useStore();

    const handleUpload = async () => {
        if (!initialNode) {
            addLog("Please select initial node", "error");
            return;
        }

        setLoading(true);
        try {
            const result = await uploadCode({
                name: codeName,
                code: code,
                entryPoint: "collect",
                initialNodeId: initialNode,
            });
            addCodePackage(result);
            addLog(`Code uploaded: ${result.id} - ${result.name}`, "success");
        } catch (error) {
            addLog(`Upload error: ${error.message}`, "error");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="control-panel">
            <h3>Control Panel</h3>

            {/* Code Editor */}
            <div className="code-editor">
                <h4>Code Editor</h4>
                <div className="editor-controls">
                    <input
                        type="text"
                        value={codeName}
                        onChange={(e) => setCodeName(e.target.value)}
                        placeholder="Code name"
                    />
                    <select
                        value={initialNode}
                        onChange={(e) => setInitialNode(e.target.value)}
                    >
                        <option value="">Initial Node...</option>
                        {nodes.map((node) => (
                            <option key={node.id} value={node.id}>
                                {node.id}
                            </option>
                        ))}
                    </select>
                </div>
                <textarea
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                    rows={8}
                    spellCheck={false}
                />
                <button
                    onClick={handleUpload}
                    disabled={loading || !initialNode}
                    className="btn-primary"
                >
                    {loading ? "Uploading..." : "Upload Code"}
                </button>
            </div>
        </div>
    );
}

export default ControlPanel;
