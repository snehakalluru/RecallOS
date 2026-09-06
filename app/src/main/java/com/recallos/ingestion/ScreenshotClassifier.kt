package com.recallos.ingestion

object ScreenshotClassifier {
    fun classify(text: String): String {
        val value = text.lowercase()
        val tags = buildList {
            if (matches(value, "receipt", "subtotal", "total", "tax", "thank you", "amount due")) {
                add("receipt")
            }
            if (matches(value, "chat", "message", "reply", "sent", "typing")) add("chat")
            if (matches(value, "http", "www", "website", "read more", "sign in")) add("webpage")
            if (matches(value, "recipe", "ingredients", "boil", "bake", "serves")) add("recipe")
            if (matches(value, "address", "street", "avenue", "apt", "delivery")) add("address")
            if (matches(value, "meeting", "agenda", "notes", "owner", "action item")) add("meeting")
            if (matches(value, "flight", "gate", "seat", "itinerary", "hotel")) add("travel")
            if (matches(value, "invoice", "invoice number", "rate", "hours")) add("invoice")
            if (matches(value, "wifi", "network", "password")) add("wifi")
            if (matches(value, "task", "todo", "to do", "buy", "call", "book")) add("tasks")
            if (matches(value, "person", "people", "woman", "man", "wearing", "dress", "saree")) {
                add("people")
            }
            if (matches(value, "watch", "wristwatch", "smartwatch")) add("watch")
            if (matches(value, "shirt", "dress", "saree", "sari", "jacket", "coat", "outfit", "clothing")) {
                add("outfit")
            }
            if (matches(value, "red", "blue", "green", "black", "white", "yellow", "pink", "orange", "purple")) {
                add("outfit-color")
            }
            if (matches(value, "bag", "purse", "necklace", "earring", "bracelet", "glasses", "hat", "accessory")) {
                add("accessories")
            }
            if (matches(value, "food", "meal", "dish", "pizza", "rice", "curry", "cake", "fruit", "coffee")) {
                add("food")
            }
            if (matches(value, "photo", "scene", "outdoor", "indoor", "landscape", "portrait")) {
                add("photo")
            }
            if (matches(value, "landmark", "temple", "museum", "beach", "mountain", "airport", "station", "city", "country")) {
                add("place")
            }
            if (isEmpty()) add("other")
        }
        return tags.joinToString(",")
    }

    private fun matches(value: String, vararg terms: String): Boolean =
        terms.any(value::contains)
}