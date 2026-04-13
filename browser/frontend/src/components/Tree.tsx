import { Link } from "react-router-dom"
type Node = {
  name: string
  children: Node[]
}
const instruments = ["RCADS","MTT", "PHQ", "GAD"]
export default function Tree({ node }: { node: Node }) {
  return (
    <div className="flex flex-col items-center relative">

      {/* Node */}
      {instruments.includes(node.name) ? (
        <Link to={`/instruments/list/${node.name}`} className="px-2 py-1 bg-amber-500 text-white shadow hover:bg-amber-600 transition duration-300 ease-in-out">
          {node.name}
        </Link>
      ) : (
      <div className="px-2 py-1 bg-amber-500 text-white shadow">
        {node.name}
      </div> )}

      {/* Children */}
      {node.children.length > 0 && (
        <>
          {/* vertical line */}
          <div className="w-px h-6 bg-gray-300" />

          <div className="flex gap-10 relative">

            {/* horizontal line */}
            {node.children.length > 1 && (
              <div className="absolute top-0 left-0 right-0 h-px bg-gray-300" />
            )}

            {node.children.map((child, i) => (
              <div key={child.name + i} className="flex flex-col items-center relative">

                {/* vertical line to child */}
                <div className="w-px h-6 bg-gray-300" />

                <Tree node={child} />

              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}