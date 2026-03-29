import { Search, BookOpen } from "lucide-react";
import InstrumentSection, { type instrumentProps } from "../components/instrumentSection";
import { Frown, ClipboardCheck } from "lucide-react";
import { Link, useNavigate} from "react-router-dom";
import { useEffect, useState } from "react";

export type Page = {
  title: string
  path: string
  description: string
  keywords: string[]
}

export const pages: Page[] = [
  {
    title: "Glossary",
    path: "/glossary",
    description: "Definitions of POEM and ontology terms",
    keywords: ["terms", "definitions", "ontology", "poem"]
  },
  {
    title: "Instruments",
    path: "/Instruments",
    description: "Browse measurement instruments",
    keywords: ["scales", "questionnaires", "measurements"]
  },
  {
    title: "Scales",
    path: "/scales",
    description: "View questionnaire scales",
    keywords: ["composite scale", "subscale"]
  },
]
const instrumentCards: instrumentProps[] = [{name:"Depression instruments", description:"Standardized tools for measuring depression", count: 100, Icon: Frown}, {name: "Anxiety instruments", description: "Standardized tools for measuring anxiety", count: 100, Icon: ClipboardCheck }]
export default function Home() {
  function searchPages(query: string, pages: Page[]): Page[] {
  const q = query.toLowerCase()
  return pages
    .map(page => {
      let score = 0

      if (page.title.toLowerCase().includes(q)) score += 3
      if (page.description.toLowerCase().includes(q)) score += 2
      if (page.keywords.some(k => k.includes(q))) score += 5

      return { ...page, score }
    })
    .filter(p => p.score > 0)
    .sort((a, b) => b.score - a.score)
}
const [query, setQuery] = useState<string>("")
const [results, setResults] = useState<Page[]>([])
const navigate = useNavigate();
const handleChnage = (e: React.ChangeEvent<HTMLInputElement>): void => {
  setQuery(e.target.value);
}
useEffect(() => {
  if (!query) {
    setResults([])
    return
  }

  const res = searchPages(query, pages)
  setResults(res)
}, [query])
  return (
    <div className="w-full">
      <section className="bg-slate-700 text-white py-25">
        <div className="mx-auto max-w-6xl px-4 text-center">
          <h1 className="text-4xl md:text-6xl tracking-tight">POEM</h1>
          <h2 className="text-3xl md:text-5xl font-bold tracking-tight">
            Psychometric Ontology of Experiences and instruments
          </h2>
          <p className="mt-5 text-base md:text-xl text-white/90 max-w-3xl mx-auto leading-relaxed">
            Broswer for mental health instruments to make assessment easier for
            researchers and clinicians.
          </p>
        </div>

        <div className=" mt-5 flex flex-col items-center justify-center text-black text-center ">
          <form
            className="max-w-xl min-w-lg bg-white p-3 space-x-1 px-3 py-2
    transition-all duration-200
    focus-within:ring-2 focus-within:ring-amber-400
    focus-within:border-amber-400 flex justify-between"
            role="search"
          >
            <label className="sr-only">Search</label>
            <input
              className="outline-none bg-transparent flex-1"
              type="search"
              placeholder="Search instrument families..."
              value = {query}
              onChange={handleChnage}
            />
            <button className="p-2 rounded-4xl transition-all duration-200 bg-amber-400 hover:bg-amber-500">
              <Search />
            </button>
          </form>
          { results && results.map((results, index) => (
            <div onClick={()=>{navigate(results.path)}} className = "text-left bg-white max-w-xl min-w-lg p-2 border border-amber-400 transition-all duration-200 hover:border-2 hover:bg-gray-200" key = {index}>{results.title}</div>
          ))}
        </div>
        <div className="flex justify-center mt-12 text-xl gap-4">
          <Link  to = "/instruments" className="px-3 py-2 gap-2 flex transition-all duration-200 bg-amber-400 hover:bg-amber-500 shadow-xl hover:shadow-2xl justify-between items-center">
            Discover Instruments<BookOpen />
          </Link >
          <button className="p-3 border border-white transition-all duration-200 hover:bg-slate-600 ">Learn More</button>
        </div>
      </section>
      <section className="mx-auto mt-12 ">
        <h2 className="text-3xl text-center mx-auto">Jump into instruments</h2>
        <div className="flex mx-auto justify-evenly items-center mt-12">
        {instrumentCards.map((card, index) => {
            return <InstrumentSection key ={index} name = {card.name} description ={card.description} count = {card.count} Icon = {card.Icon}/>
        })}
        </div>
      </section>
     <section className="mx-auto mt-12 mb-12 flex justify-center">
      <Link to="/scales" className="text-2xl text-center p-3 border hover:bg-gray-50">Scales</Link>
      </section>
    </div>
  );
}
