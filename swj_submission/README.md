# POEM-SWJsubmission

## Individual file
The files provided include an example of POEM's usage on currently available data about the [Revised Child Anxietry and Depression Scale (RCADS)](https://rcads.ucla.edu/), [My Thoughts about Therapy (MTT)](https://www.childfirst.ucla.edu/resources/), and the [nine-item Patient Health Questionnaire (PHQ-9)](https://www.apa.org/depression-guideline/patient-health-questionnaire.pdf). The individual files are divided as described below for ease of compilation and browsing. The included data is not intended to reflect the final state of the RCADS, MTT, or PHQ-9, and is only intended to show how existing questionnaires might align with POEM. 

### activities.ttl 
- includes descriptions of creation, translation, testing, and modification of questionnaires

### codebooks.ttl
- includes Codebook individuals
- includes Codebook & Experience relationships
  - codebook sio:hasAttribute experience

### experiences.ttl
- includes Experience individuals

### informants.ttl
- includes Informant individuals

### instruments.ttl
- includes PsychometricQuestionnaire individuals
- includes PsychometricQuestionnaire & Informant relationships
  - <http://purl.org/twc/poem/individual/instrument/*> sio:hasAttribute <http://purl.org/twc/poem/individual/informant/*>
- includes PsychometricQuestionnaire & Item relationships
  - instrument sio:hasMember item

### items.ttl
- includes OrdinalPosition bnodes for Item positions
- includes OrdinalPosition & Instrument relationships
  - _:* sio:inRelationTo instrument
- includes Item individuals
- includes Item & OrdinalPosition relationships
  - item sio:hasAttribute _:*
- includes Item & Codebook relationships
  - item sio:hasAttribute _:*, codebook
- includes Item and ItemStem relationships
   item sio:hasSource itemStem

### itemStemConcepts.ttl
- includes ItemStemConcept individuals

### itemStems.ttl
- includes ItemStem individuals
- includes ItemStem and ItemStemConcept relationships
  - itemStem sio:hasSource itemStemConcept

### responseOptions.ttl
- includes OrdinalPosition bnodes for ResponseOption individuals
- includes OrdinalPosition & Codebook relationships
  - _:* sio:inRelationTo codebook
- includes ResponseOption individuals
- includes ResponseOption & OrdinalPosition relationships
  - responseOption sio:hasAttribute _:*

### scaleItemConceptMap.ttl
- includes Scale & ItemStemConcept relationships
  - scale sio:hasMember itemStemConcept

### scales.ttl
- includes QuestionnaireScale individuals
