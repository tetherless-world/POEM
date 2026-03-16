import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
type Params = {
  id: string;
};
export default function IndividualInstrumentPage()
{   
    const { id } = useParams<Params>();
    const [instrument, setInstrument] = useState({informant:"", language: ""});
    const [items, setItems] = useState([]);
    const [components, setComponents] = useState([]);
    const [loading, setLoading] = useState(true);
    useEffect(() => {
        if(!id) return;
        const fetchData = async () => {
            try {
                loading && setLoading(true);
                const res = await fetch(`http://127.0.0.1:8000/instrument/individual/${id}`);
                const data = await res.json();
                setInstrument(data.instrument);
                setItems(data.items);
                setComponents(data.components);
            } catch (error) {
                console.error("Error fetching instrument details:", error);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [id]);

    if (loading) {
        return <div>Loading...</div>;
    }

    return (<div>
        <section className="bg-slate-700 text-white py-20">
            <div className="mx-auto max-w-6xl px-4 flex flex-col gap-8">
        <h1 className="text-4xl md:text-5xl font-bold tracking-tight">{id}</h1>

        <div className="flex justify-between ">
            {id?.includes("Y") ? <Link className = "p-2 rounded-xl bg-white text-black text-xl w-fit hover:bg-gray-100 hover:scale-105 trasition duration-300 ease-in-out" to = {`/instruments/individual/${id.replace("Y", "CG")}`}>View Caregiver Version</Link>: id?.includes("CG") ? <Link className = "p-2 rounded-xl bg-white text-black text-xl w-fit hover:bg-gray-100 hover:scale-105 trasition duration-300 ease-in-out" to = {`/instruments/individual/${id.replace("CG", "Y")}`}>View Youth Version</Link>: null}
            <Link className = "p-2 rounded-xl bg-white text-black text-xl w-fit hover:bg-gray-100 hover:scale-105 trasition duration-300 ease-in-out" to = {`/instruments/list/${id ? id.split("-").slice(0, 1).join("") : "RCADS"}`} >View Instrument List</Link>
        </div>
        <div className="text-2xl ">
            {instrument && <p className = "text-amber-500">{instrument.informant}</p>}
            {instrument && <p className = "mt-6 w-fit text-green-700 bg-green-400 hover:bg-green-500 rounded-xl p-2 trasition duration-300 ease-in-out">{instrument.language}</p>}
        </div>
            </div>
        </section>
        <section className = "mt-12 flex gap-12 justify-center mx-auto">
            <div>
            <h2 className = "text-2xl text-center text-bold text-slate-600 font-bold">Items</h2>

            {items && items.map((item: any, index: number) => (
                <div key={index} className="mx-auto mt-12 p-6 rounded-2xl bg-white shadow-md hover:shadow-xl trasition duration-200 ease-in-out hover:scale-105 max-w-4xl">
                    <p className="text-lg">{`${index + 1}. ${item}`}</p>
                </div>
            ))}
            </div>
            <div>

            <h2 className = "text-2xl text-center text-bold text-slate-600 font-bold">Components</h2>

            {components && components.map((component: any, index: number) => (
                <div key={index} className="mx-auto mt-12 p-6 rounded-2xl bg-white shadow-md hover:shadow-xl trasition duration-200 ease-in-out hover:scale-105 max-w-4xl">
                    <p className="text-lg">{`${index + 1}. ${component}`}</p>
                </div>
            ))}
            </div>
        </section>
    </div>)
}