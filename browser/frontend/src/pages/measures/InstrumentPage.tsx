import { useEffect, useState } from "react";
import { useParams } from "react-router-dom"
import CountBox from "../../components/countBox";
type Params = {
    id: string;
}
const descriptions: Record<string, String> = {
    rcads: "The Revised Children's Anxiety and Depression Scale (RCADS) measures the reported frequency of various symptoms of anxiety and low mood.",
    gad7: "The GAD-7 (General Anxiety Disorder-7) measures severity of anxiety"
}
export default function InstrumentPage(){
    const { id } = useParams<Params>();
    const [name, setName] = useState("");
    const [instrumentCount, setInstrumentCount] = useState(0);
    const [languageCount, setLanguageCount] = useState(0);
    const [caregiver, setCaregiver] = useState([""]);
    const [youth, setYouth] = useState([""]);
    useEffect(() => {
                setName(id ? id :"");
    },[])
    useEffect(() => {
         const getInstrumentInfo = async () => {
            try {
                const res = await fetch(`http://127.0.0.1:8000/${name}`);
                const data = await res.json();
                setInstrumentCount(data.count);
                setLanguageCount(data.languages);
                setCaregiver(data.itemConcepts.caregiver)
                setYouth(data.itemConcepts.youth);
                console.log(data);

            }catch(err){
                console.error("Fetch failed:", err);
            }
        }
        getInstrumentInfo();
    },[name])
    return (<div>
        <section className="bg-slate-700 text-white py-20">
        <div className="mx-auto max-w-6xl px-4 ">
          <h1 className="text-4xl md:text-5xl font-bold tracking-tight">
            {id?.toUpperCase()}
          </h1>

          <p className="mt-5 text-base md:text-lg text-white/90 max-w-3xl  leading-relaxed">
            {descriptions[name]}
          </p>
        </div>
      </section>
      <section className="mx-auto">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mx-auto mt-12">
                    <CountBox count = {instrumentCount} description = "total instruments" />
                    <CountBox count = {languageCount} description="total languages" />
                </div>
      </section>
      <section className="flex justify-center gap-6 mt-12 mx-auto">
        <div className="text-md">
            <h2 className="font-bold text-lg text-center">Youth Questions</h2>
            <div className="flex flex-col gap-y-6">
            {youth.map((y, index)=> {
                return<div className="shadow-sm hover:shadow-xl text-center w-1/2 hover:shadow-amber-200 rounded-2xl py-3 px-2 mx-auto transition-all duration-200 ease-in-out">{`${index + 1}. ${y}`}</div>
            })}
            </div>
        </div>
        <div className="text-md"><h2 className="font-bold text-lg text-center">Caregiver Questions</h2>
        <div className="flex flex-col gap-y-6">
              {caregiver.map((c, index)=> {
                return <div className="shadow-sm hover:shadow-xl text-center w-1/2 hover:shadow-amber-200 rounded-2xl py-3 px-2 mx-auto transition-all duration-200 ease-in-out">{`${index + 1}. ${c}`}</div>
            })}
        </div>
        </div>
      </section>
    </div>)
}