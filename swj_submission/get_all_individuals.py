from rdflib import Graph
import os

def main():
    g = Graph()

    files = os.listdir('./')
    for file in files:
        if file.endswith('.ttl') and file != 'individuals_full.ttl':
            print("parsing {}".format(file))
            g.parse(file)
            print("parsed {}".format(file))

    g.serialize(destination='./individuals_full.ttl')

if __name__ == "__main__":
    main()