export default function Scales(props: { scales: string[] }) {
  return (
    <div className="flex flex-col gap-6 items-center mt-12 "> <h2 className="text-2xl font-bold text-slate-600">Scales</h2>
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 w-full flex-wrap items-center justify-center">
    {props.scales.map((scale, index) => (
      <p key={index} className="shadow-md w-10/12 hover:shadow-2xl text-xl p-5 m-2 rounded-xl transition duration-300 ease-in-out hover:scale-105 ">
        {scale }</p>
    ))} </div>
    </div>
  );
}