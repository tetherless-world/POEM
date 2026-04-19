import { useEffect, useState } from "react";
import type {Page, apiRes} from "./Home";
import { Link } from "react-router-dom";
import { apiFetch } from "../api/api";
import { useSearchParams } from "react-router-dom";
import  { staticPages } from "./Home"
export default function Search(){
    const [results, setResults] =  useState<Page[]>([]);
    const [searchParams] = useSearchParams()
    const query: string  = searchParams.get("q") || ""
     async function searchGraph(query: string){
        const q = query.toLowerCase();
        try{
          const res = await(apiFetch(`/search/${q}`,{
            method: "POST"
          }))
          const data = await res.json();
          const convertToPages: Page[]  = data.map((page: apiRes): Page  => {
         return {title: page.name, path: page.path, description: "", keywords: []}
        });
          return convertToPages;
        }catch(err){
          console.error(err);
          return [];
        }
      }
      function searchPages(query: string, pages: Page[]): Page[] {
        const q = query.toLowerCase();
        return pages
          .map((page) => {
            let score = 0;
    
            if (page.title.toLowerCase().includes(q)) score += 3;
            if (page.description.toLowerCase().includes(q)) score += 2;
            if (page.keywords.some((k) => k.includes(q))) score += 5;
    
            return { ...page, score };
          })
          .filter((p) => p.score > 0)
          .sort((a, b) => b.score - a.score);
      }
       useEffect(() => {
    if (!query) {
      setResults([]);
      return;
    }
     const runSearch = async (): Promise<void> => {
    const res = searchPages(query, staticPages);
    const seen = new Set();
    const uniqueRes = res.filter(item => {
      const isDup = seen.has(item.path);
      seen.add(item.path);
      return !isDup;
    })
    setResults(uniqueRes);

    const apiSearch = await searchGraph(query);
    const updatedUnique = apiSearch.filter(item => {
      const isDup = seen.has(item.path);
      seen.add(item.path);
      return !isDup;
    })
    setResults((prev: Page[]): Page[] => {
      return [...prev, ...updatedUnique];
    });
  };
  runSearch();
  }, [query]);
     return (
        <div className="flex flex-col gap-6 items-center mt-12 "> <h2 className="text-3xl font-bold text-slate-600">Search Results</h2>
        {results.map((result, index) => (
          <Link to= {`/${result.path}`} key={index} className="shadow-md hover:shadow-2xl w-10/12 text-xl p-5 m-2  border-2 border-gray-200 transition duration-300 ease-in-out hover:scale-105 ">
            {result.title}</Link>
        ))}
        </div>
      );
}