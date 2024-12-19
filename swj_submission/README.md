# POEM-SWJsubmission

## Individual files
The files provided include an example of POEM's usage on currently available data about the [Revised Child Anxietry and Depression Scale (RCADS)](https://rcads.ucla.edu/), [My Thoughts about Therapy (MTT)](https://www.childfirst.ucla.edu/resources/), and the [nine-item Patient Health Questionnaire (PHQ-9)](https://www.apa.org/depression-guideline/patient-health-questionnaire.pdf). The individual files are divided as described below for ease of compilation and browsing. The included data is not intended to reflect the complete or final state of the RCADS, MTT, or PHQ-9, and is only intended to show how existing questionnaires might align with POEM. Additionally, our current modeling is subject to change.

### activities.ttl 
- includes descriptions of creation, translation, testing, and modification of questionnaires

### codebooks.ttl
- includes Codebook individuals
- includes Codebook and Experience relationships
  - codebook sio:hasAttribute experience
 
### constructs.ttl
- includes subset of SNOMED individuals used in the RCADS as Symptoms and Disorders
- Includes Symptom and Disorder relationships
  - disorder poem:hasSymptom symptom
- Includes Scale and Construct/Disorder relationships
  - scale sio:isAbout disorder
- Includes Item and Construct/Symptom relationships
  - item sio:isAbout symptom

### experiences.ttl
- includes Experience individuals

### informants.ttl
- includes Informant individuals

### instrumentCollections.ttl
- includes InstrumentCollection individuals
- includes InstrumentCollection and Instrument relationships
  - instrumentCollection sio:hasMember instrument

### instruments.ttl
- includes PsychometricQuestionnaire individuals
- includes PsychometricQuestionnaire and Informant relationships
  - instrument sio:hasAttribute informant
- includes PsychometricQuestionnaire and Item relationships
  - instrument sio:hasMember item

### itemStemConcepts.ttl
- includes ItemStemConcept individuals

### itemStems.ttl
- includes ItemStem individuals
- includes ItemStem and ItemStemConcept relationships
  - itemStem sio:hasSource itemStemConcept

### items.ttl
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

### responseOptions.ttl
- includes OrdinalPosition bnodes for ResponseOption individuals
- includes OrdinalPosition and Codebook relationships
  - _:* sio:inRelationTo codebook
- includes ResponseOption individuals
- includes ResponseOption and OrdinalPosition relationships
  - responseOption sio:hasAttribute _:*

### scaleItemConceptMap.ttl
- includes Scale and ItemStemConcept relationships
  - scale sio:hasMember itemStemConcept

### scales.ttl
- includes QuestionnaireScale individuals
