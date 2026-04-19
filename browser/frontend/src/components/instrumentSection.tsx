import type { LucideIcon } from "lucide-react";
import { useNavigate } from "react-router-dom";
export type instrumentProps = {
  name: string;
  description: string;
  count: number;
  Icon?: LucideIcon;
};
export default function InstrumentSection({
  name,
  description,
  count,
  Icon,
}: instrumentProps) {
  const navigate = useNavigate();
  return (
    <div className="border-2 shadow-xl hover:shadow-2xl space-y-5">
      <div className="h-3 bg-amber-500 ">
      </div>
      <div className="p-3">
        <div className="flex justify-between">
          {Icon ? <Icon /> : null}
          <p>{count}</p>
        </div>
        <h3 className="text-xl">{name}</h3>
        <p>{description}</p>
        <button className = "border-2 bg-amber-500 hover:bg-amber-600 p-2 mt-4"onClick={()=> navigate(`/search?q=${name}`)}>Explore </button>
      </div>
    </div>
  );
}
