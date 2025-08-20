# NHBosses (Paper 1.21+)
Plugin de boss configurable avec **scale** (taille), spawn par biomes, capacités périodiques, barre de boss, loots YAML et commandes d'admin.

## Fonctionnalités
- Option `scale` par boss (ex. géants x6).
- 5 nouveaux boss : Maître Breeze, Titan Warden, L'Évocateur fou, Ravageur Alpha, L'Ensorceleuse.
- Capacités : `wind_burst`, `sonic_pulse`, `darkness_curse`, `vex_call`, `potion_cloud`, `stomp_quake` + capacités de base.
- 100% API Paper (pas de NMS), compatible 1.21+.

## Build (Maven)
1. **JDK 17+** requis.
2. `mvn -q -DskipTests package`
3. Le jar sort dans `target/NHBosses.jar`.
4. Copier le jar dans `plugins/` d'un serveur **Paper 1.21+**, démarrer une fois, puis éditer `config.yml`.

## Commandes
- `/boss list` — liste des boss dispo
- `/boss spawn <id>` — spawn un boss à ta position
- `/boss killall` — retire tous les boss actifs

## IDs fournis
- `maitre_breeze`, `titan_warden`, `evocateur_fou`, `ravageur_alpha`, `ensorceleuse`
- (exemples bonus) `desert_wyrm`, `frost_revenant`, `nether_brute`, `forest_ent`

## Structure
```
NHBosses/
  ├─ pom.xml
  ├─ src/main/resources/
  │   ├─ plugin.yml
  │   └─ config.yml
  └─ src/main/java/com/nowheberg/bosses/
      ├─ BossesPlugin.java
      ├─ BossManager.java
      ├─ BossInstance.java
      ├─ BossCommand.java
      └─ BossListeners.java
```

## GitHub rapide
```bash
git init
git add .
git commit -m "NHBosses 1.0.0 initial"
git branch -M main
git remote add origin <votre-repo.git>
git push -u origin main
```