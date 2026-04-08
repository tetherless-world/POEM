import { useParams } from "react-router-dom";
import { useEffect, useState } from "react";    
import InstrumentCard from "../../../components/instrumentCard";
import { apiFetch } from "../../../api/api";
type Params = {
  id: string;
};
export default function InstrumentList() {

const { id } = useParams<Params>();
const [instruments, setInstruments] = useState([]);
const getInstrumentsList = async () => { 
    try {
        const res = await apiFetch(`/instruments/${id}`);
        const data = await res.json();
        setInstruments(data.instruments);
        console.log(data);
    } catch (err) {
        console.error("Fetch failed:", err);
    }
}
useEffect(() => {
    getInstrumentsList();
}, [id]);
    return (<div>
        <h1 className="text-center text-3xl mt-12 text-slate-600 font-bold">{id?.toUpperCase()} Instruments</h1>
<div className="mt-12">
       { instruments && <div className="flex flex-col gap-4">
            {instruments.map((instrument: any, index: number) => (
                <InstrumentCard key={index} name={instrument.name} lang={instrument.lang} informant={instrument.informant} count={instrument.count} />
            ))}
        </div>  }</div>
    </div>)
}