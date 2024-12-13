from rdflib import Graph, Literal, Namespace
from rdflib.namespace import RDF, XSD
import os

def main():
    g = Graph()

    files = os.listdir('./')
    for file in files:
        if file.endswith('.ttl') and file != 'individuals_full.ttl':
            print("parsing {}".format(file))
            g.parse(file)
            print("parsed {}".format(file))

    OWL = Namespace("http://www.w3.org/2002/07/owl#")
    g.bind("owl", OWL)
    g.add((OWL.Ontology, OWL.imports, Literal("https://raw.githubusercontent.com/tetherless-world/POEM/evidence-modeling/POEM.rdf", datatype=XSD.string)))

    g.serialize(destination='./individuals_full.ttl')

if __name__ == "__main__":
    main()