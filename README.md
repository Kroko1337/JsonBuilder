# 📦 JsonBuilder

**JsonBuilder** is a command-line utility that converts a Maven `pom.xml` file into a launcher-friendly JSON configuration.  
It is particularly useful for environments like Minecraft modding, custom clients, or any setup that requires JSON-based dependency resolution.

---

## 🔧 Features

- ✅ Converts Maven `pom.xml` to JSON
- ✅ Customizable output via command-line arguments
- ✅ Supports "fast mode" for quick processing
- ✅ Optional dependency downloading

---

## Requirements:

- Java 8 or higher
- Maven (XML File)

---

## Available Arguments

| Argument     | Description                                           | Default                          |
| ------------ | ----------------------------------------------------- | -------------------------------- |
| `--input`    | Path to the input `pom.xml` file                      | `pom.xml`                        |
| `--name`     | Name of the output JSON file                          | Inferred from project name       |
| `--version`  | Version string to include in the JSON output          | `1.8`                            |
| `--main`     | Main class path of the application                    | `net.minecraft.client.main.Main` |
| `--fastmode` | Enables faster parsing (true/false)                   | `false`                          |
| `--download` | Downloads dependencies during processing (true/false) | `false`                          |

---

## 📁 Output

The tool generates a JSON file containing:

- Project metadata
- All dependencies declared in the `pom.xml`
- Dependency paths (if `--fastmode` is disabled)
- Main class entry and version string

---

## 📝 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

---

## 🤝 Contributing

Suggestions, pull requests, and improvements are welcome!  
Just fork the repo and submit your changes via a pull request.
