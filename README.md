# Module OpenConcerto d'impression d'étiquettes sur imprimante USB Brother QL

Ce projet est un plugin pour l'ERP OpenConcerto permettant l'impression d'étiquettes d'articles sur les imprimantes USB Brother QL,
sans nécessiter de driver.

Son objectif est de pouvoir exploiter toutes les capacités de ces imprimantes, en particulier de pouvoir imprimer des étiquettes
de tailles variables sur les rouleaux continus.

Le module a été développé en Java, et en premier à destination d'un système d'exploitation Gnu/Linux, mais peut fonctionner sur d'autres systèmes.

Il se base sur les librairies :
- [brotherql](https://github.com/vxel/brotherql) pour l'interfaçage avec les imprimantes USB Brother
- [usb4java](http://usb4java.org/) pour l'intégration de libusb avec Java
- [OkapiBarcode](https://github.com/woo-j/OkapiBarcode) pour la génération de codes barres

Le module est sous license GNU General Public License v3.0.

Développeur : Cédric de Launois
                           
![Screenshot](openconcerto-label-brotherql-screenshot.png)


# Imprimantes supportées

Le code a été testé sous Linux avec une imprimante Brother QL-700, mais il devrait fonctionner avec les imprimantes suivantes :
QL-500, QL-550, QL-560, QL-570, QL-580N, QL-600, QL-650TD, QL-700, QL-700M, QL-710W, QL-720NW, QL-800, QL-810W, QL-820NWB, QL-1050, QL-1060N, QL-1100, QL-1110NWB, QL-1115NWB
        
# Téléchargement

- [org.delaunois.openconcerto.label.brotherql-1.2.jar](https://raw.githubusercontent.com/vxel/openconcerto-label-brotherql/refs/heads/main/dist/org.delaunois.openconcerto.label.brotherql-1.2.jar)

# Installation

Le module s'installe comme tout module OpenConcerto, en le copiant dans le répertoire des modules puis en l'activant 
via l'interface de gestion des modules.

## Linux

Sur Linux, l'accès à l'imprimante demande des droits USB en lecture et écriture.
En cas de permission insuffisante, il peut être nécessaire d'ajouter une règle udev, de la façon suivante :

- Créer une nouvelle règle udev : 
```
sudo vi /etc/udev/rules.d/50-brotherusb.rules
```
- Ecrire dans le fichier (par exemple) la règle suivane : 
```
SUBSYSTEMS=="usb", ATTRS{idVendor}=="04f9", MODE="0666"`
```
- Recharger les règles : 
```
sudo udevadm control --reload
```

# Compilation depuis les sources

Le module se compile avec [Maven](https://maven.apache.org/).
L'API d'interfaçage avec OpenConcerto n'étant pas diponible en tant que librairie Java dans Maven Central, 
la compilation nécessite la présence du jar [openconcerto](https://www.openconcerto.org/fr/telechargement.html) 1.7.4 dans le repository Maven local.
Pour l'y ajouter, téléchargez le jar `OpenConcerto.jar` puis lancez la commande suivante :
```
mvn install:install-file -Dfile=lib/OpenConcerto.jar -DgroupId=org.openconcerto -DartifactId=openconcerto -Dversion=1.7.4  -Dpackaging=jar -DgeneratePom=true
```

Le module se compile ensuite avec la commade Maven : `mvn package`
