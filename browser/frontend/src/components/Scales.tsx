export default function Scales(props: { scales: string[] }) {
  return (
    <div className="flex flex-col gap-4 items-center mt-12"> <h2 className="text-2xl font-bold">Scales</h2>
    {props.scales.map((scale, index) => (
      <p key={index} className="shadow-md hover:shadow-2xl text-xl p-3 rounded-2xl">
        {scale }</p>
    ))}
    </div>
  );
}