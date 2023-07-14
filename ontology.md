---
title: Ontology
---

### Conceptual Diagram

![POEM Conceptual Diagram](images/POEM_UML.png)

This diagram gives an overview of important concepts included in the POEM ontology.

##### hasco:SemanticVariable

A *semantic variable* is a variable specification that does not include a population property; e.g. two variables have the same semantic variable when the only distinction between them is their population. The Entity (sio:Object/TargetHuman) and Attribute (sio:Attribute/SymptomExperience) properties of a *semantic variable* must be specified.

##### Symptom Experience

Each Response Option to an Item indicates some Symptom Experience; e.g. in Question 1 of the RCADS-47, which asks “I worry about things”, the “often” response indicates the Symptom Experience individual representing the experience of often feeling worry.

### Ontology

- [POEM][poem-current]
- [POEM-RCADS][poem-rcads-current]
  - An example usage of the POEM ontology, showing how it is able to model questionnaires using the Revised Children's Anxiety and Depression Scale (RCADS)- full 47-item version and shorter 25-item version included.
  - [Revised Children's Anxiety and Depression Scale](https://www.childfirst.ucla.edu/resources/) created by Dr. Bruce Chorpita (UCLA)

### Ontologies Reused

| Ontology                                | Prefix | Resource                                     |
|-----------------------------------------|--------|----------------------------------------------|
| Human-Aware Science Ontology            | hasco  | https://hadatac.org/description/ont/hasco    |
| Virtual Solar Terrestrial Observatory   | vsto   | https://hadatac.org/description/ont/vstoi    |
| Semanticscience Integrated Ontology     | sio    | https://semanticscience.org/ontology/sio.owl |

[poem-current]: https://raw.githubusercontent.com/tetherless-world/POEM/main/POEM.rdf?token=GHSAT0AAAAAACFDP63DLXPW45SZMFHELD2MZFRZDSQ
[poem-rcads-current]: https://raw.githubusercontent.com/tetherless-world/POEM/main/POEM-RCADS.rdf?token=GHSAT0AAAAAACFDP63DRO54JNYJXFSIXKGUZFRZMIQ