# Guide : de zéro à l'APK installé sur ton téléphone

## Ce que fait l'application
- **Stock** : ajouter/supprimer des produits (nom, quantité, prix)
- **Ventes** : vendre un produit (le stock diminue automatiquement), historique des ventes
- **Rapports** : chiffre d'affaires du jour / semaine / mois, top produits vendus
- **Synchronisation** : plusieurs téléphones utilisant le même "code boutique" voient les mêmes données en temps réel (via Firebase)

---

## Étape 1 — Créer le projet Firebase (gratuit)

1. Va sur https://console.firebase.google.com
2. **Ajouter un projet** → nomme-le (ex: `MonStock`) → continue jusqu'à **Créer le projet**
3. Menu gauche → **Compilation → Firestore Database** → **Créer une base de données** → mode **Production** → choisis une région
4. Menu gauche → **Compilation → Authentication** → **Commencer** → active **Email/Mot de passe**
5. Icône ⚙️ → **Paramètres du projet** → onglet "Général" → section "Vos applications" → clique l'icône **Android** (</>)
6. Nom du package Android : `com.monstock.app` (exactement, sans faute)
7. Termine l'assistant et **télécharge `google-services.json`**
8. Dans le projet que je t'ai préparé, **remplace** le fichier `app/google-services.json` par celui que tu viens de télécharger

### Sécuriser la base (important)
Dans Firestore, onglet **Règles**, remplace par :
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /shops/{shopCode}/{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```
Cela autorise tout utilisateur connecté à lire/écrire — suffisant pour démarrer. Publie les règles.

---

## Étape 2 — Mettre le code sur GitHub

1. Crée un compte sur https://github.com si besoin
2. **New repository** → nom `monstock-app` → **Create repository**
3. Sur la page du repo vide, clique **"uploading an existing file"**
4. Glisse-dépose TOUS les fichiers/dossiers du projet (garde la même structure de dossiers)
5. **Commit changes**

*(Astuce : compresse le dossier du projet, puis décompresse-le avant l'upload si GitHub n'accepte pas les dossiers glissés directement — sinon utilise GitHub Desktop, plus simple pour les dossiers.)*

---

## Étape 3 — Compiler l'APK avec Codemagic (en ligne, gratuit)

1. Va sur https://codemagic.io → **Sign up** avec ton compte GitHub
2. **Add application** → sélectionne ton repo `monstock-app`
3. Codemagic détecte le fichier `codemagic.yaml` déjà inclus dans le projet — choisis le workflow **"MonStock Android Build"**
4. Clique **Start new build**
5. Attends la fin (quelques minutes) → un fichier `.apk` apparaît dans les **Artifacts** de la page de build
6. Télécharge l'APK sur ton téléphone (via le lien, ou envoie-toi le fichier par email/drive)

---

## Étape 4 — Installer l'APK sur ton téléphone

1. Ouvre le fichier `.apk` téléchargé sur ton téléphone
2. Android va demander d'autoriser "Installer des applications inconnues" pour l'application utilisée (ex: navigateur, fichiers) → autorise-le
3. Installe l'application

---

## Étape 5 — Utiliser l'application

1. Au premier lancement, **crée un compte** (email + mot de passe) et choisis un **code boutique** (ex: `epicerie-jean`)
2. Sur un 2e téléphone, connecte-toi avec un compte (même différent) mais **le même code boutique** → il verra le même stock

---

## En cas de problème
- Erreur de connexion Firebase → vérifie que `google-services.json` a bien été remplacé et que le nom du package est bien `com.monstock.app`
- Le build Codemagic échoue → regarde les logs, l'erreur la plus fréquente est un `google-services.json` mal formé
- Question sur un message d'erreur précis → montre-moi le message, je t'aiderai à le résoudre
