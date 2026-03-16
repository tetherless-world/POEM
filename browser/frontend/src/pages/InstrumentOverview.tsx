import { Link } from "react-router-dom";
import {  useEffect, useState } from "react";
import Tree from "../components/Tree";
type Node = {
  name: string;
  children: Node[];
};
function createTree(data: any): Node[] {
  if (data === null || data === undefined) return [];
  if(typeof data === "string") {
    return [{name: data, children: []}];
  }
  if(Array.isArray(data)) {
    return data.flatMap(createTree);
  }
  if(typeof data === "object") {
    return Object.entries(data).map(([key, value]) => ({
      name: key,
      children: createTree(value),
    }));
  }
  return [];
}

export default function InstrumentOveriew() {
  const [treeData, setTreeData] = useState<Node[]>([]);
  const fetchTreeData = async () => {
    try {
      const res = await fetch("http://localhost:8000/all_instruments_by_scale");
      const data = await res.json();
      let newTreeData: Node[] | undefined = createTree(data);
      setTreeData(newTreeData || [{ name: "No data", children: undefined }]);
      console.log("Fetched tree data:", data);
    } catch (error) {
      console.error("Error fetching tree data:", error);
    }
  };
  useEffect(() => {
    fetchTreeData();
  }, []);
  return (
    <div>
      {" "}
      <section className="bg-slate-700 text-white py-20">
        <div className="mx-auto max-w-6xl px-4 text-center">
          <h1 className="text-4xl md:text-5xl font-bold tracking-tight">
            Instruments Overview
          </h1>

          <p className="mt-5 text-base md:text-lg text-white/90 max-w-3xl mx-auto leading-relaxed">
            Browse psychometric instruments structured with POEM in order to
            support faster selection, reuse, and comparison.
          </p>
        </div>
      </section>
      <section className="text-center mt-10 ">
        <div className="max-w-6xl px-4 mx-auto">
          <h2 className="text-slate-700 text-7xl font-bold">169</h2>
          <p className="text-lg mt-2">Available Instruments</p>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-12 justify-items-center">
          <div className="bg-white rounded-xl shadow-md p-6 hover:shadow-xl trasition duration-300 ease-in-out hover:scale-105 border-2 border-gray-200">
            <h3 className="text-4xl font-bold text-slate-600">38</h3>
            <p>Languages Available</p>
          </div>
          <div className="bg-white rounded-xl shadow-md p-6 hover:shadow-xl trasition duration-300 ease-in-out hover:scale-105 border-2 border-gray-200">
            <h3 className="text-4xl font-bold text-slate-600">3</h3>
            <p>Instrument Families</p>
          </div>
        </div>
      </section>
      <section className="mt-12 text-center">
        <div>
          <h2 className="text-3xl">Mental Health Tree</h2>
          <p>click any node to explore</p>
        </div>
        <div className=" relative max-w-6xl mx-auto bg-white rounded-2xl border-2 border-gray-200 shadow-xl p-12">
          <svg
            className="pointer-events-none absolute inset-0 h-full w-full"
            viewBox="0 0 1000 500"
            preserveAspectRatio="none"
          >
            <line
              x1="500"
              y1="90"
              x2="500"
              y2="180"
              stroke="#CBD5E1"
              strokeWidth="4"
            />
            <line
              x1="350"
              y1="180"
              x2="650"
              y2="180"
              stroke="#CBD5E1"
              strokeWidth="4"
            />
            <line
              x1="350"
              y1="180"
              x2="350"
              y2="300"
              stroke="#CBD5E1"
              strokeWidth="4"
            />
            <line
              x1="500"
              y1="180"
              x2="500"
              y2="400"
              stroke="#CBD5E1"
              strokeWidth="4"
            />
            <line
              x1="650"
              y1="180"
              x2="650"
              y2="400"
              stroke="#CBD5E1"
              strokeWidth="4"
            />
          </svg>

          <div className="relative ">
            <div>
              <button className="bg-amber-500 text-white rounded-3xl shadow-lg hover:bg-amber-600 text-xl p-5 transition duration-300 ease-in-out hover:scale-110">
                Mental Health instruments
              </button>
            </div>
            <div className="flex justify-center gap-12 mt-12">
              <div className="flex flex-col items-center space-y-8">
                <button className="bg-slate-500 text-white rounded-3xl shadow-lg hover:bg-slate-600 text-xl px-5 py-3 trasition duration-300 ease-in-out hover:scale-110">
                  Treatments
                </button>
                <Link
                  to="/instruments/mtt"
                  className="bg-gray-500 text-white shadow-lg hover:bg-gray-600 text-lg p-2 trasition duration-300 ease-in-out hover:scale-110 rounded-sm"
                >
                  MTT
                </Link>
              </div>
              <div className="flex flex-col items-center space-y-8">
                <button className="bg-slate-500 text-white rounded-3xl shadow-lg hover:bg-slate-600 text-xl px-5 py-3 trasition duration-300 ease-in-out hover:scale-110">
                  Anxiety
                </button>
                <Link
                  to="/instruments/rcads"
                  className="bg-gray-500 text-white shadow-lg hover:bg-gray-600 text-lg p-2 trasition duration-300 ease-in-out hover:scale-110 rounded-sm"
                >
                  RCADS
                </Link>
                <Link
                  to="/instruments/gad7"
                  className="bg-gray-500 text-white shadow-lg hover:bg-gray-600 text-lg p-2 trasition duration-300 ease-in-out hover:scale-110 rounded-sm"
                >
                  GAD-7
                </Link>
              </div>
              <div className="flex flex-col items-center space-y-8">
                <button className="bg-slate-500 text-white rounded-3xl shadow-lg hover:bg-slate-600 text-xl px-5 py-3 trasition duration-300 ease-in-out hover:scale-110">
                  Depression
                </button>
                <Link
                  to="/instruments/rcads"
                  className="bg-gray-500 text-white shadow-lg hover:bg-gray-600 text-lg p-2 trasition duration-300 ease-in-out hover:scale-110 w-fit rounded-sm"
                >
                  RCADS
                </Link>
                <Link
                  to="/instruments/phq"
                  className="bg-gray-500 text-white shadow-lg hover:bg-gray-600 text-lg p-2 trasition duration-300 ease-in-out hover:scale-110 w-fit rounded-sm"
                >
                  PHQ
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>
      <section  className="mx-auto text-sm">
        <Tree node={{ name: "Mental Health Instruments", children: treeData }} />
      </section>
      <section className="text-center mt-12 space-y-4 mx-auto w-1/2 ">
        <h2 className="text-3xl ">Common Instrument Families</h2>
        <p className="">
          Each instrument family contains different versions designed for
          specific use cases
        </p>
        <div className="border-2 border-amber-500 text-left text-amber-900 px-5 py-3 bg-gradient-to-r from-amber-500/80 to-amber-600/80 rounded-xl">
          <h3 className="font-bold">Common Instruments</h3>
          <p>
            A instrument family groups related instruments that assess the same
            or closely related mental health constructs (e.g., depression,
            anxiety, well-being). Families may include full-length instruments,
            short forms, revised editions, and validated language adaptations
            designed for different populations and research contexts. Within
            POEM, instruments are represented as structured entities that
            explicitly link instruments to their items, scales, constructs,
            versions, and supporting evidence. This structured representation
            enables users to explore relationships across instruments, compare
            instruments assessing similar constructs, and trace the provenance
            of adaptations and revisions. By organizing psychometric instruments
            into coherent families and modeling their internal structure, POEM
            supports semantic search, reproducibility, and interoperability
            across research studies and digital systems. This approach advances
            the discoverability and responsible reuse of mental health
            instruments in both clinical and research settings.
          </p>
        </div>
      </section>
      <section className="mt-12 mx-auto max-w-6xl">
        <div className="shadow-xl">
          <div className="flex justify-between items-center p-4 bg-gradient-to-r rounded-t-3xl from-amber-600/80 to-yellow-600/80 text-white">
            <div>
              <h2 className="text-2xl font bold">RCADS family</h2>
              <p>Revised Child Anxiety and Depression Scale</p>
            </div>
            <div className="border border-gray-300 rounded-md">
              <button className="bg-amber-500/30 p-2">View All Versions</button>
            </div>
          </div>
          <div className="border-b-2 border-gray-100 p-5">
            <p>
              A youth and caregiver diagnostic for detecting levels of anxiety
              and depression
            </p>
          </div>
          <h3 className="m-5 text-xl">Latest versions</h3>
          <div className="flex gap-6 p-5">
            <div className="flex-1 border border-gray-200 rounded-2xl hover:shadow-lg p-3 mx-auto bg-gray-300/20">
              <div className="space-y-1 w-full">
                <div className="flex items-center  justify-between">
                  <div>
                    <h4 className="text-xl">
                      RCADS-25{" "}
                      <span className="font-normal text-xs text-green-700 px-2 py-1 bg-green-300 rounded-3xl">
                        v1
                      </span>
                    </h4>
                  </div>
                  <div className="text-right text-sm text-gray-400">
                    <p>25 items</p>
                    <p>10 min</p>
                  </div>
                </div>
                <div className="text-gray-500">
                  <p>Comprehensive Screening</p>
                </div>
                <div className="text-gray-900">
                  <p>Comprehensive 25 item depression and anxiety tool</p>
                </div>
                <div className="flex justify-end rounded-md ">
                  <button className="border border-gray-300 rounded-md px-2 py-1">
                    view details
                  </button>
                </div>
              </div>
            </div>
            <div className="flex-1 border border-gray-200 rounded-2xl hover:shadow-lg p-3 mx-auto bg-gray-300/20">
              <div className="space-y-1 w-full">
                <div className="flex items-center  justify-between">
                  <div>
                    <h4 className="text-xl">
                      RCADS-47{" "}
                      <span className=" font-normal text-xs text-green-700 px-2 py-1 bg-green-300 rounded-3xl">
                        v1
                      </span>
                    </h4>
                  </div>
                  <div className="text-right text-sm text-gray-400">
                    <p>47 items</p>
                    <p>15 min</p>
                  </div>
                </div>
                <div className="text-gray-500">
                  <p>Full Screening</p>
                </div>
                <div className="text-gray-900">
                  <p>Full 47 item depression and anxiety tool</p>
                </div>
                <div className="flex justify-end rounded-md">
                  <button className="border border-gray-300 rounded-md px-2 py-1">
                    view details
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
