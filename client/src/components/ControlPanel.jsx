import { useState } from "react";
import { uploadCode } from "../services/api";
import useStore from "../store/useStore";
import {
    demoTemplates,
    defaultTemplate,
    templateCategories,
} from "../templates/demoTemplates";

function ControlPanel({ nodes }) {
    const [code, setCode] = useState(defaultTemplate.code);
    const [codeName, setCodeName] = useState(defaultTemplate.name);
    const [entryPoint, setEntryPoint] = useState(defaultTemplate.entryPoint);
    const [initialNode, setInitialNode] = useState("");
    const [loading, setLoading] = useState(false);
    const [selectedTemplate, setSelectedTemplate] = useState(
        "fibonacciAccumulator",
    );

    const { addLog, addCodePackage } = useStore();

    const handleTemplateChange = (templateKey) => {
        setSelectedTemplate(templateKey);
        const template = demoTemplates[templateKey];
        setCode(template.code);
        setCodeName(template.name);
        setEntryPoint(template.entryPoint);
    };

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
                entryPoint: entryPoint,
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

                {/* Template Selector */}
                <div
                    className="template-selector"
                    style={{ marginBottom: "10px" }}
                >
                    <label
                        style={{
                            display: "block",
                            marginBottom: "5px",
                            fontWeight: "bold",
                        }}
                    >
                        Demo Templates:
                    </label>
                    <select
                        value={selectedTemplate}
                        onChange={(e) => handleTemplateChange(e.target.value)}
                        style={{
                            width: "100%",
                            padding: "8px",
                            marginBottom: "5px",
                        }}
                    >
                        {Object.entries(templateCategories).map(
                            ([categoryKey, category]) => (
                                <optgroup
                                    key={categoryKey}
                                    label={category.label}
                                >
                                    {category.templates.map((templateKey) => (
                                        <option
                                            key={templateKey}
                                            value={templateKey}
                                        >
                                            {demoTemplates[templateKey].name} -{" "}
                                            {
                                                demoTemplates[templateKey]
                                                    .description
                                            }
                                        </option>
                                    ))}
                                </optgroup>
                            ),
                        )}
                    </select>
                </div>

                <div className="editor-controls">
                    <input
                        type="text"
                        value={codeName}
                        onChange={(e) => setCodeName(e.target.value)}
                        placeholder="Code name"
                    />
                    <input
                        type="text"
                        value={entryPoint}
                        onChange={(e) => setEntryPoint(e.target.value)}
                        placeholder="Entry point"
                        style={{ width: "150px" }}
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
                    rows={15}
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
