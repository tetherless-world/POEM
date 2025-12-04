# POEM
Psychometric Ontology of Experiences and Measures

https://tetherless-world.github.io/POEM/

### Abstract
Psychometrics is the field relating to the measurement of concepts within psychology, particularly the assessment of
various social and psychological dimensions in humans. The relationship between entities such as an assessment instrument and its component elements, the subject and respondent, and the latent variables being assessed, is critical to finding an appropriate assessment instrument, especially in the context of clinical psychology and mental healthcare in which providing the best care based on empirical evidence is crucial. The current standard for questionnaire-based assessment relies on text-based distributions of instruments; so, a structured representation is necessary to capture these relationships to enhance accessibility and use of existing measures, encourage reuse of questionnaires and their component elements, and enable sophisticated reasoning over assessment instruments and results by increasing interoperability. We present the design process and architecture of such a domain ontology, the Psychometric Ontology of Experiences and Measures, situating it within the context of related ontologies, and demonstrating its practical utility through evaluation against a series of competency questions concerning the creation, use, and reuse of psychometric questionnaires in clinical, research, and development settings.

![](https://raw.githubusercontent.com/tetherless-world/POEM/refs/heads/TGDK_revision/images/UML_vertical_TGDK_revision.png)

## [POEM.rdf](POEM.rdf)
This file contains the POEM ontology. It imports the Semanticscience Integrated Ontology (SIO) in its entirety, but future plans are underway to apply MIREOT principles and import only necessary classes and axioms from SIO.

## Individual files
The files provided include an example of POEM's usage on currently available data about the [Revised Child Anxietry and Depression Scale (RCADS)](https://rcads.ucla.edu/), [My Thoughts about Therapy (MTT)](https://www.childfirst.ucla.edu/resources/), and the [nine-item Patient Health Questionnaire (PHQ-9)](https://www.apa.org/depression-guideline/patient-health-questionnaire.pdf). The individual files are divided as described below for ease of compilation and browsing. The included data is not intended to reflect the complete or final state of the RCADS, MTT, or PHQ-9, and is only intended to show how existing questionnaires might align with POEM. Additionally, our current modeling is subject to change.

### [individualsFull.ttl](individualsFull.ttl)
- includes all entities and relations in the following .ttl files

### [activities.ttl](individuals/activities.ttl) 
- includes descriptions of creation, translation, testing, and modification of questionnaires

### [codebooks.ttl](individuals/codebooks.ttl)
- includes Codebook individuals
- includes Codebook and Experience relationships
  - codebook sio:hasAttribute experience
 
### [constructs.ttl](individuals/constructs.ttl)
- includes subset of SNOMED individuals used in the RCADS as Symptoms and Disorders
- Includes Symptom and Disorder relationships
  - disorder poem:hasSymptom symptom
- Includes Scale and Construct/Disorder relationships
  - scale sio:isAbout disorder
- Includes Item and Construct/Symptom relationships
  - item sio:isAbout symptom

### [experiences.ttl](individuals/experiences.ttl)
- includes Experience individuals

### [informants.ttl](individuals/informants.ttl)
- includes Informant individuals

### [instrumentCollections.ttl](individuals/instrumentCollections.ttl)
- includes InstrumentCollection individuals
- includes InstrumentCollection and Instrument relationships
  - instrumentCollection sio:hasMember instrument

### [instruments.ttl](individuals/instruments.ttl)
- includes PsychometricQuestionnaire individuals
- includes PsychometricQuestionnaire and Informant relationships
  - instrument sio:hasAttribute informant
- includes PsychometricQuestionnaire and Item relationships
  - instrument sio:hasMember item

### [itemStemConcepts.ttl](individuals/itemStemConcepts.ttl)
- includes ItemStemConcept individuals

### [itemStems.ttl](individuals/itemStems.ttl)
- includes ItemStem individuals
- includes ItemStem and ItemStemConcept relationships
  - itemStem sio:hasSource itemStemConcept

### [items.ttl](individuals/items.ttl)
- includes OrdinalPosition bnodes for Item positions
- includes OrdinalPosition and Instrument relationships
  - _:* sio:inRelationTo instrument
- includes Item individuals
- includes Item and OrdinalPosition relationships
  - item sio:hasAttribute _:*
- includes Item and Codebook relationships
  - item sio:hasAttribute _:*, codebook
- includes Item and ItemStem relationships
   item sio:hasSource itemStem

### [responseOptions.ttl](individuals/responseOptions.ttl)
- includes OrdinalPosition bnodes for ResponseOption individuals
- includes OrdinalPosition and Codebook relationships
  - _:* sio:inRelationTo codebook
- includes ResponseOption individuals
- includes ResponseOption and OrdinalPosition relationships
  - responseOption sio:hasAttribute _:*

### [scaleItemConceptMap.ttl](individuals/scaleItemConceptMap.ttl)
- includes Scale and ItemStemConcept relationships
  - scale sio:hasMember itemStemConcept

### [scales.ttl](individuals/scales.ttl)
- includes QuestionnaireScale individuals

