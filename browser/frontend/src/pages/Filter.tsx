import { useEffect, useState } from "react";
import { apiFetch } from "../api/api";
import { Link } from "react-router-dom";
type FilterRequest = {
  scale: string[];
  language: string[];
  informant: string[];

};
type scaleLanguageInformant ={
    scales: string[],
    languages: string[],
    informants: string[],
}

type FilterGroupProps = {
    title: string;
    mode: string;
    options: string[];
    selected: string[];
    onToggle: (value: string) => void;
    onClear: () => void;
}

function FilterGroup({title, mode, options, selected, onToggle, onClear}: FilterGroupProps) {
    return (
        <section className="border border-gray-200 bg-white p-3 shadow-sm">
            <div className="mb-3 flex items-center justify-between gap-2">
                <div>
                    <h2 className="text-sm font-bold text-slate-700">{title}</h2>
                    <p className="text-xs font-semibold uppercase text-amber-700">{mode}</p>
                </div>
                {selected.length > 0 && (
                    <button
                        type="button"
                        onClick={onClear}
                        className="border border-gray-300 px-2 py-1 text-xs text-slate-600 transition hover:bg-gray-100"
                    >
                        Clear
                    </button>
                )}
            </div>
            <div className="max-h-64 space-y-2 overflow-y-auto pr-1">
                {options.map((option) => {
                    const isSelected = selected.includes(option);
                    return (
                        <label
                            key={option}
                            className={`flex cursor-pointer items-center gap-2 border px-3 py-2 text-sm transition ${
                                isSelected
                                    ? "border-amber-500 bg-amber-50 text-slate-900"
                                    : "border-gray-200 text-slate-700 hover:border-gray-300 hover:bg-gray-50"
                            }`}
                        >
                            <input
                                type="checkbox"
                                checked={isSelected}
                                onChange={() => onToggle(option)}
                                className="h-4 w-4 accent-amber-500"
                            />
                            <span className="min-w-0 flex-1 break-words">{option}</span>
                        </label>
                    );
                })}
            </div>
        </section>
    );
}

export default function Filter(){
    const [searchResults, setSearchResults] = useState<any[]>([]);
    const [SLI, setSLI] = useState<scaleLanguageInformant>({scales:[], languages: [], informants:[]});
    const [scale, setScale] = useState<string[]>([]);
    const [language, setLanguage] = useState<string[]>([]);
    const [informant, setInformant] = useState<string[]>([]);
    const toggleFilter = (value: string, selected: string[], setSelected: (values: string[]) => void) => {
        setSelected(selected.includes(value)
            ? selected.filter((item) => item !== value)
            : [...selected, value]
        );
    };
    async function searchWithFilter(scale: string[], language: string[], informant:string[]){
        try{
            const paylod: FilterRequest = {scale: scale, language: language, informant: informant};
            const res = await(apiFetch('/search_filter',{
                method: "POST",
                  headers: {
      'Content-Type': 'application/json', // Inform server you are sending JSON
    },
        body: JSON.stringify(paylod)
            }))
            const data = await res.json();
            return data;
        }catch(err){
            console.error(err);
            return [];
        }
    }
    async function getScalesLanguagesInformants(){
        try{
            const res = await(apiFetch('/s_l_i'))
            const data = await res.json();
            return data;
        }catch(err){
            console.error(err);
            return {scales: [], languages: [], informants: []};
        }
    }
    useEffect(()=>{
        async function loadData(){
        const sLIResults = await getScalesLanguagesInformants();
        const filterResults =await searchWithFilter([],[],[]);
        setSearchResults(filterResults);
        setSLI(sLIResults);
        }
        loadData();
        console.log()
    },[])

    useEffect(() => {
  async function runFilter() {
    const results = await searchWithFilter(
      scale,
      language,
        informant
    );
    setSearchResults(results);
  }

  runFilter();
}, [scale, language, informant]);
    const selectedFilters = [...scale, ...language, ...informant];

    return(<div className="flex flex-col items-center px-4">
        <h1 className="text-3xl font-bold text-slate-600">Filter Instruments</h1>
       <div className="mt-6 flex w-full max-w-6xl flex-col gap-6 md:flex-row md:items-start">
      <aside className="flex w-full flex-col gap-4 md:w-80 md:shrink-0">
        <div className="border border-gray-200 bg-slate-50 p-3">
            <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-bold text-slate-700">Active Filters</p>
                {selectedFilters.length > 0 && (
                    <button
                        type="button"
                        onClick={() => {
                            setScale([]);
                            setLanguage([]);
                            setInformant([]);
                        }}
                        className="border border-gray-300 bg-white px-2 py-1 text-xs text-slate-600 transition hover:bg-gray-100"
                    >
                        Clear all
                    </button>
                )}
            </div>
            <div className="mt-3 flex flex-wrap gap-2">
                {selectedFilters.length === 0 ? (
                    <span className="text-sm text-slate-500">Showing all instruments</span>
                ) : selectedFilters.map((filter) => (
                    <span key={filter} className="bg-amber-100 px-2 py-1 text-xs font-semibold text-amber-800">
                        {filter}
                    </span>
                ))}
            </div>
        </div>

        <FilterGroup
            title="Scales"
            mode="match all selected"
            options={SLI.scales}
            selected={scale}
            onToggle={(value) => toggleFilter(value, scale, setScale)}
            onClear={() => setScale([])}
        />

        <FilterGroup
            title="Languages"
            mode="match any selected"
            options={SLI.languages}
            selected={language}
            onToggle={(value) => toggleFilter(value, language, setLanguage)}
            onClear={() => setLanguage([])}
        />

        <FilterGroup
            title="Informants"
            mode="match any selected"
            options={SLI.informants}
            selected={informant}
            onToggle={(value) => toggleFilter(value, informant, setInformant)}
            onClear={() => setInformant([])}
        />

      </aside>
    <div className="flex w-full flex-1 flex-col gap-3">
        {searchResults && searchResults.map((result, index) =>
            <Link key = {index} to={`/instruments/individual/${result}`} className="w-full border-2 border-gray-200 p-5 text-xl shadow-md transition duration-300 ease-in-out hover:scale-105 hover:shadow-2xl">{result}</Link>
        )}</div>
    </div>
</div>
    )
}
