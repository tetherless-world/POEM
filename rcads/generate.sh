#!/bin/ash

echo "Generating RDF files from RML mappings..."
echo "Instruments..."
java -jar rmlmapper-7.1.2-r374-all.jar -m rml-instruments.ttl -o ../poem-demo/public/data/instruments.ttl -s turtle
echo "Informants..."
java -jar rmlmapper-7.1.2-r374-all.jar -m rml-informants.ttl -o ../poem-demo/public/data/informants.ttl -s turtle
echo "ItemStemConcepts..."
java -jar rmlmapper-7.1.2-r374-all.jar -m rml-itemStemConcepts.ttl -o ../poem-demo/public/data/itemStemConcepts.ttl -s turtle
echo "ItemStems..."
java -jar rmlmapper-7.1.2-r374-all.jar -m rml-itemStems.ttl -o ../poem-demo/public/data/itemStems.ttl -s turtle
echo "Items..."
java -jar rmlmapper-7.1.2-r374-all.jar -m rml-items.ttl -o ../poem-demo/public/data/items.ttl -s turtle
echo "InstrumentItemMap..."
java -jar rmlmapper-7.1.2-r374-all.jar -m rml-instrumentItemMap.ttl -o ../poem-demo/public/data/instrumentItemMap.ttl -s turtle
echo "Codebooks..."
java -jar rmlmapper-7.1.2-r374-all.jar -m rml-codebooks.ttl -o ../poem-demo/public/data/codebooks.ttl -s turtle
echo "ResponseOptions..."
java -jar rmlmapper-7.1.2-r374-all.jar -m rml-responseOptions.ttl -o ../poem-demo/public/data/responseOptions.ttl -s turtle
echo "Scales..."
java -jar rmlmapper-7.1.2-r374-all.jar -m rml-scales.ttl -o ../poem-demo/public/data/scales.ttl -s turtle
echo "ScaleItemConceptMap..."
java -jar rmlmapper-7.1.2-r374-all.jar -m rml-scaleItemConceptMap.ttl -o ../poem-demo/public/data/scaleItemConceptMap.ttl -s turtle
echo "Experiences..."
java -jar rmlmapper-7.1.2-r374-all.jar -m rml-experiences.ttl -o ../poem-demo/public/data/experiences.ttl -s turtle
echo "Activities..."
java -jar rmlmapper-7.1.2-r374-all.jar -m rml-activities.ttl -o ../poem-demo/public/data/activities.ttl -s turtle