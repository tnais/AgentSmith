## Technology choices

Do no add maven dependencies withouth explaining the purpose and asking confirmation

# Java Source Code Formatting and Style Guide

This document defines the standard formatting rules and architectural style guidelines for writing clean, maintainable, and idiomatic Java source code.

---

## 1. Source File Structure

A Java source file must follow a strict structural order. Every section must be separated by exactly **one blank line**.

1. **License or Copyright Information** (if applicable)
2. **Package Statement** (No line-wrapping allowed)
3. **Import Statements** (Grouped, no wildcards `*`)
4. **Exactly One Top-Level Class** (Matches the file name exactly)

### File Basics
* **Encoding:** All source files must be encoded in **UTF-8**.
* **Whitespace:** Only the ASCII horizontal space character (`0x20`) is used for whitespace. Tabs are **strictly prohibited** for indentation.

---

## 2. Formatting & Layout

### Indentation and Line Limits
* **Indentation:** Use **2 spaces** per indentation level, as configured in `.editorconfig`. Do not use tabs.
* **Column Limit:** A strict **80-character** limit applies to all lines.
* **Line Wrapping:** When breaking a long line, indent the continuation line by at least **+4 spaces** relative to the original line. Break before operators, not after them (except for assignment).

### Braces and Blocks
Java utilizes the **Kernighan and Ritchie (K&R)** brace style ("Egyptian braces") for blocks:
* No line break before the opening brace (`{`).
* Line break after the opening brace (`{`).
* Line break before the closing brace (`}`).
* Line break after the closing brace (`}`) *unless* it terminates a statement or is followed by `else`, `catch`, or `finally`.

```java
// Correct K&R Style Example
if (condition) {
    executeTask();
} else {
    rollback();
}
```

---

## 3. Naming Conventions

Identifiers must clearly communicate intent using specific letter casing depending on the element type.

| Element | Casing Style | Example | Notes |
| :--- | :--- | :--- | :--- |
| **Classes & Interfaces** | `UpperCamelCase` | `UrlHttpRequest` | Must be nouns or noun phrases. |
| **Methods** | `lowerCamelCase` | `findPostById()` | Must be verbs or verb phrases. |
| **Variables & Parameters** | `lowerCamelCase` | `username` | Avoid single-character names except for loop counters. |
| **Constants** | `UPPER_CASE` | `MAX_RETRY_LIMIT` | Must be `static final` and truly immutable. |

*Note: Acronyms should be treated as words (e.g., `XmlHttpRequest` instead of `XMLHTTPRequest`).*

---

## 4. Coding Practices

* **Annotations:** Always use the `@Override` annotation when overriding a method or implementing an interface method.
* **Variable Declarations:** Prefer **one variable declaration per line**. Do not write `String username, email;`.
* **Modifiers:** Access level modifiers (`public`, `protected`, `private`) should be explicitly defined for classes, methods, and member variables.
* **Static Members:** Always qualify static members using the class name, never through an object instance (e.g., Use `Math.sqrt()`, not `myMathInstance.sqrt()`).
* **Long Literals:** Always use an uppercase `L` suffix for long literals (e.g., `3000000000L`) to prevent confusion with the digit `1`.

---

## 5. Documentation Comments (Javadoc)

Starting with **Java 23**, Java natively supports CommonMark-based **Markdown documentation comments** using triple slashes (`///`) as defined in [JEP 467](https://openjdk.org "JEP 467"). This replaces traditional HTML tags.

* **Formatting:** Use `///` for documentation blocks instead of `/** ... */`.
* **Hierarchy:** Use a single hashtag (`#`) for the class description and double hashtags (`##`) for method sections.
* **Code blocks:** Use triple backticks (```java) for syntax-highlighted code examples inside your documentation.
* **Tags:** Standard Javadoc tags like `@param`, `@return`, and `@throws` are still fully supported alongside Markdown syntax.

```java
/// # UserService
/// Provides core authentication and user management facilities.
///
/// ## Usage Example
/// ```java
/// UserService service = new UserService();
/// User user = service.findPostById("USR-102");
/// ```
public class UserService {

    /// Finds a user profile using their unique identifier string.
    ///
    /// @param urlId The unique system ID.
    /// @return The corresponding [User] object.
    /// @throws UserNotFoundException if the ID is missing.
    public User findPostById(String urlId) throws UserNotFoundException {
        // Implementation here
    }
}
```

## Proposed package structure

Use the existing base package `org.gianoziaorientale` and organize the new implementation by responsibility:

Comment code
Document APIs
