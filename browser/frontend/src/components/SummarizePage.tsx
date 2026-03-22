import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
export default function SummarizePage() {
  const [isOpen, setIsOpen] = useState(false);
  const [summary, setSummary] = useState("");
  const [loading, setLoading] = useState(false);
  const location = useLocation();
  const extractPageContent = (): string => {
  const main = document.querySelector("main") || document.body;
  main.querySelectorAll("script, style, nav, footer").forEach(el => el.remove());

  return main.innerText;
};
  const payload =  {
        url: window.location.href,
        content: extractPageContent(),
      };
  const summarize = async () => {
    setLoading(true);
    try{
    console.log("summary running");
    if (summary !== "") return;
    const res = await fetch("http://localhost:8000/ai_summary", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });

    const data = await res.json();
    setSummary(data.summary);
}catch(err){
    console.error(err);
    setSummary("Error fetching Summary");
}finally{
    setLoading(false);
}
  };
  useEffect(() => {
    setSummary("")
  },[location.pathname])
  const toggleChatBot = () => {
    setIsOpen(!isOpen);
  
  };
  useEffect(() => {
    if(isOpen){
        summarize()
    }
  },[isOpen])

  return (
    <div className="fixed bottom-4 right-4 z-50 w-[250px]">
      <div
        className={`mt-2  p-2 bg-white  border-x border-t shadow-lg transition-all duration-300 ease-in-out
                    overflow-y-auto overflow-x-hidden break-words
                    ${isOpen ? "h-[300px] opacity-100" : "max-h-0 opacity-0 pointer-events-none"}`}
      >{summary}
     {loading ? <p className="text-center">Loading ...</p> : <></>}
      </div>
      <button
        onClick={toggleChatBot}
        className="bg-amber-500 w-[250px] text-white px-4 py-2 shadow-lg hover:bg-amber-600 transition duration-300 ease-in-out"
      >
        {isOpen ? "Close Summary" : "Summarize Page"}
      </button>
    </div>
  );
}
