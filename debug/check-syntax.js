import path from 'path';
import { fileURLToPath, pathToFileURL } from 'url';
import fs from 'fs';

const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
const sourceDirectory = path.resolve(currentDirectory, '..', 'src', 'main', 'java');

process.argv[2] ??= sourceDirectory;

const originalExtname = path.extname;
path.extname = (filePath) => {
  const ext = originalExtname(filePath);
  return ext === '.kt' || ext === '.java' ? '.js' : ext;
};

const originalReadFileSync = fs.readFileSync;
fs.readFileSync = (filePath, options) => {
  if (typeof filePath === 'string' && (filePath.endsWith('.kt') || filePath.endsWith('.java'))) {
    return originalReadFileSync(filePath, options)
      .toString()
      .replace(/("""[\s\S]*?""")/g, '``');
  }

  return originalReadFileSync(filePath, options);
};

await import(pathToFileURL('C:/Users/ADMIN/.codex/skills/check-syntax/resources/check-syntax.js').href);
