import { Github } from "lucide-react";

export default function Footer()
{
    return (<footer className="border-t bg-gray-200 p-5 mt-12">
        <div className="">
        <h2 className="bold text-2xl">Psychometric Ontology of Experiences and Measures</h2>
        </div>
        <div>
            <p> Broswer for mental health instruments to make assessment easier for
            researchers and clinicians.</p>
        </div>
        <div className="flex"><Github/></div>
    </footer>)
}