import { useState } from "react";
export default function SummarizePage() {
    const [isOpen, setIsOpen] = useState(false);

    const toggleChatBot = () => {
        setIsOpen(!isOpen);
    };
    return (
        <div className="fixed bottom-4 right-4 z-50">
            <button
                onClick={toggleChatBot}
                className="bg-amber-500 w-[250px] text-white px-4 py-2 shadow-lg hover:bg-amber-600 transition duration-300 ease-in-out"
            >
                {isOpen ? "Close Summary" : "Summarize Page"}
            </button>

            <div
                className={`mt-2 overflow-hidden rounded-lg bg-white shadow-lg transition-all duration-300 ease-in-out
                    ${isOpen ? "max-h-[300px] opacity-100" : "max-h-0 opacity-0 pointer-events-none"}`}
            >
                
            </div>
        </div>
    );
}