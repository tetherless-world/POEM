import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import CountBox from "../../components/countBox";
import Scales from "../../components/Scales";
type Params = {
  id: string;
};
const descriptions: Record<string, String> = {
  rcads:
    "The Revised Children's Anxiety and Depression Scale (RCADS) measures the reported frequency of various symptoms of anxiety and low mood.",
  gad7: "The GAD-7 (General Anxiety Disorder-7) measures severity of anxiety",
  mtt: "The My Thoughts about Therapy instrument (MTT) is a 35-item questionnaire with 5 subscales,each with 7 items, that represent the multidimensional REACH framework for characterizingtreatment engagement",
  phq: "The Patient Health Questionnaire-9 (PHQ-9) is a widely used, 9-question self-administered tool for screening and assessing the severity of depression in adults.",
};
const adultInstruments = ["gad7", "phq"];
export default function InstrumentPage() {
  const { id } = useParams<Params>();
  const [name, setName] = useState("");
  const [instrumentCount, setInstrumentCount] = useState(0);
  const [languageCount, setLanguageCount] = useState(0);
  const [caregiver, setCaregiver] = useState([""]);
  const [youth, setYouth] = useState([""]);
  const [scales, setScales] = useState([""]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setName(id ? id : "");
  }, []);
  useEffect(() => {
    const getInstrumentInfo = async () => {
      try {
        const res = await fetch(`http://127.0.0.1:8000/${name}`);
        const data = await res.json();
        setInstrumentCount(data.count);
        setLanguageCount(data.languages);
        setCaregiver(data.itemConcepts.caregiver);
        setYouth(data.itemConcepts.youth);
        if(data.scales !== undefined && data.scales.length > 0){ 
          setScales(data.scales);
        }
        console.log(data);
        setLoading(false);
      } catch (err) {
        console.error("Fetch failed:", err);
      }
    };
    getInstrumentInfo();
  }, [name]);
  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen ">
        <p className="p-5 rounded-xl bg-gradient-to-r from-amber-400 to-amber-500 ">
          Loading...
        </p>
      </div>
    );
  }
  return (
    <div>
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
          <CountBox count={instrumentCount} description="total instruments" />
          <CountBox count={languageCount} description="total languages" />
        </div>
      </section>
      <section className="flex justify-center gap-6 mt-12 mx-auto">
        {adultInstruments.includes(name) ? (
          <div className="text-center">
            <h2 className="text-2xl font-bold">Respondent</h2>{" "}
            <p className="shadow-md hover:shadow-2xl p-3  rounded-2xl">Adult</p>{" "}
          </div>
        ) : (
          <div className="text-center">
            <h2 className="text-2xl font-bold">Respondents</h2> <div className="flex justify-center gap-2" >
            <p className="shadow-md hover:shadow-2xl text-xl p-3 rounded-2xl">Youth</p>

            <p className="shadow-md hover:shadow-2xl text-xl p-3 rounded-2xl">Caregiver</p>
            </div>
          </div>
        )}
      </section>
      <section className="flex justify-center">
        {id !== "gad7" ?<Scales scales={scales} /> : <></> }
      </section>
    </div>
  );
}
