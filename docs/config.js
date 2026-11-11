window.MONSTOCK_CONFIG = {
  apiKey: "AIzaSyB-EWPvbTLngy-xVWDAaWYv5mXSIGoxzgA",
  projectId: "monstock-e62d3",
  appId: "1:611269480514:android:a57d88f276fdec2ef81ddb",
  shopTitle: "Commandez ici",

  // Indicatif pays ajouté automatiquement aux numéros locaux (Guinée = 224).
  countryCode: "224",

  // Paiement à distance : numéros de la boutique affichés au client quand il choisit Wave / Orange Money.
  // Laisse number vide ("") pour ne rien afficher. "link" est facultatif (lien de paiement marchand,
  // {amount} sera remplacé par le montant).
  payments: {
    "Wave": { number: "", link: "" },
    "Orange Money": { number: "", link: "" }
  }
};
