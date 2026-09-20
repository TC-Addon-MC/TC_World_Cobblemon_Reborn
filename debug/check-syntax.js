import path from 'path';
import { fileURLToPath, pathToFileURL } from 'url';

const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
process.argv[2] ??= path.resolve(currentDirectory, '..', 'src');
await import(pathToFileURL('C:/Users/ADMIN/.codex/skills/check-syntax/resources/check-syntax.js').href);
