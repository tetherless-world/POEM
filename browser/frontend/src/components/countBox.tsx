type countBox = {
    count: number;
    description: string;
}
export default function CountBox({count, description}: countBox) {
     return <div className="bg-white rounded-xl shadow-md p-6 hover:shadow-xl trasition duration-300 ease-in-out hover:scale-105 border-2 border-gray-200 mx-auto">
            <h3 className="text-4xl font-bold text-slate-600">{count}</h3>
            <p>{description}</p>
          </div>
}