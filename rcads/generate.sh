#!/bin/sh

echo "Generating RDF files from RML mappings..."
echo "Instruments..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-instruments.ttl -o ../poem-demo/dist/data/instruments.ttl -s turtle
echo "Informants..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-informants.ttl -o ../poem-demo/dist/data/informants.ttl -s turtle
echo "ItemStemConcepts..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-itemStemConcepts.ttl -o ../poem-demo/dist/data/itemStemConcepts.ttl -s turtle
echo "ItemStems..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-itemStems.ttl -o ../poem-demo/dist/data/itemStems.ttl -s turtle
echo "Items..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-items.ttl -o ../poem-demo/dist/data/items.ttl -s turtle
echo "InstrumentItemMap..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-instrumentItemMap.ttl -o ../poem-demo/dist/data/instrumentItemMap.ttl -s turtle
echo "Codebooks..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-codebooks.ttl -o ../poem-demo/dist/data/codebooks.ttl -s turtle
echo "ResponseOptions..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-responseOptions.ttl -o ../poem-demo/dist/data/responseOptions.ttl -s turtle
echo "Scales..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-scales.ttl -o ../poem-demo/dist/data/scales.ttl -s turtle
echo "ScaleItemConceptMap..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-scaleItemConceptMap.ttl -o ../poem-demo/dist/data/scaleItemConceptMap.ttl -s turtle
echo "Experiences..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-experiences.ttl -o ../poem-demo/dist/data/experiences.ttl -s turtle
echo "Activities..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-activities.ttl -o ../poem-demo/dist/data/activities.ttl -s turtle
echo "ScalesInstrument..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-scalesInstrument.ttl -o ../poem-demo/dist/data/scalesInstrument.ttl -s turtle
echo "InstrumentComponents..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-components.ttl -o ../poem-demo/dist/data/components.ttl -s turtle
echo "Languages..."
java -jar rmlmapper-8.0.0-r378-all.jar -m rml-languages.ttl -o ../poem-demo/dist/data/languages.ttl -s turtle