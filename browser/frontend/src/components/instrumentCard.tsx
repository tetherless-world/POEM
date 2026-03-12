import { Link } from "react-router-dom";
export default function InstrumentCard(props: { name: string; lang: string; informant: string; count: number }) {
  return (
    <Link  to = {`/instruments/individual/${props.name}`} className="bg-white rounded-xl shadow-md p-6 hover:shadow-xl trasition duration-300 ease-in-out hover:scale-105 border-2 border-gray-200 mx-auto w-10/12">
    <div className="flex justify-between"> <h3 className="text-2xl font-bold text-slate-600">{props.name}</h3> <p className="text-green-700 bg-green-400 hover:bg-green-500 rounded-xl p-2 trasition duration-300 ease-in-out"> {props.lang}</p></div> 
     <div className="text-slate-600 flex justify-between mt-4">
      <p>{props.informant}</p>
      <p>{props.count} items</p>
      </div> 
    </Link>
  );
}