export default function Glossary(){


const terms = [
  {
    term: "Construct",
    description: "A hypothetical or theoretical entity that cannot be directly observed.",
    ontology: "sio:Entity",
    category: "POEM"
  },
  {
    term: "Psychometric Questionnaire",
    description: "A questionnaire used to measure an individual’s mental capabilities, behaviors, or psychological traits.",
    ontology: "vstoi:Questionnaire",
    category: "POEM"
  },
  {
    term: "Questionnaire Scale",
    description: "A collection of indicators designed to be related to a shared construct.",
    ontology: "sio:Object",
    category: "POEM"
  },
  {
    term: "Composite Scale",
    description: "A scale containing two or more subscales.",
    ontology: "poem:QuestionnaireScale",
    category: "POEM"
  },
  {
    term: "Experience",
    description: "An informant’s observation or encounter in a situation or event.",
    ontology: "sio:Quality",
    category: "POEM"
  },
  {
    term: "Item Stem Concept",
    description: "The experience or construct that a question or statement is intended to capture.",
    ontology: "sio:Object",
    category: "POEM"
  },
  {
    term: "Instrument Family",
    description: "A set of measurement instruments derived from each other or closely related.",
    ontology: "sio:Collection",
    category: "POEM"
  },
  {
    term: "HASCO Semantic Variable",
    description: "A variable specification that includes target entities and attributes, but not the population.",
    ontology: "owl:Thing",
    category: "HASCO"
  },
  {
    term: "Detector",
    description: "A device which detects measurements.",
    ontology: "sio:Device",
    category: "VSTO-I"
  },
  {
    term: "Instrument",
    description: "A device or mechanism used to acquire attribute values of entities of interest.",
    ontology: "sio:Device",
    category: "VSTO-I"
  },
  {
    term: "Questionnaire",
    description: "An instrument used to acquire data reported from human subjects.",
    ontology: "vstoi:Instrument",
    category: "VSTO-I"
  },
  {
    term: "Item",
    description: "An item stem and its response option within an assessment.",
    ontology: "vstoi:Detector",
    category: "VSTO-I"
  },
  {
    term: "Item Stem",
    description: "A question or statement used to prompt an individual to provide information regarding a latent variable.",
    ontology: "sio:Object",
    category: "VSTO-I"
  },
  {
    term: "Scale",
    description: "A collection of indicators designed to be related to a shared construct.",
    ontology: "vstoi:Instrument",
    category: "VSTO-I"
  },
  {
    term: "Codebook",
    description: "A document used to outline the content, format, and coding scheme of a dataset.",
    ontology: "sio:Entity",
    category: "VSTO-I"
  },
  {
    term: "Response Option",
    description: "A possible answer choice provided for a question or statement.",
    ontology: "sio:Object",
    category: "VSTO-I"
  },
  {
    term: "Informant",
    description: "An individual who provides information based on their observations.",
    ontology: "sio:Entity",
    category: "VSTO-I"
  }
];
    return <div className="flex flex-col mt-12">
        <h1 className="font-bold text-3xl text-center text-slate-600">
        Glossary
        </h1>
    <div className="flex flex-col text-md">
        {terms.map((term) => (
  <div className="rounded-none shadow-md mx-auto w-10/12 hover:shadow-xl p-6 border-2 border-gray-200 mt-12 transition duration-300 ease-in-out" key={term.term}>
    <div className="flex justify-between">
      <h2 className="text-2xl font-bold text-slate-600">{term.term}</h2>
      <p>{term.category}</p>
    </div>
    <p>{term.description}</p>
  </div>
))}
</div>
    </div>
}
