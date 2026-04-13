import type { LucideIcon } from "lucide-react";

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
        <button>Explore </button>
      </div>
    </div>
  );
}
