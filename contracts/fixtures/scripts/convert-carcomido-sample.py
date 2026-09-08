#!/usr/bin/env python3
"""
Ticket 008 -- convierte UNA VEZ el sample real de Blockbench
(galgoth_studio_build_pack/samples/carcomido_minecraft_cuboids.bbmodel)
a un MobProjectModel (contracts/fixtures/carcomido-mob-project-model.json),
para el development harness del viewport Three.js.

Deliberadamente NO es un BBModelImporter de producción -- el proyecto
excluye explícitamente esa feature (ver docs/definiciones/galgoth-studio-mvp.md,
nota del ticket 012: "no BBModelImporter feature, test-only parsing").
Este script se corre a mano cuando el sample cambie y su salida se
versiona como fixture estática; el frontend nunca parsea .bbmodel en
runtime.

Uso: python3 contracts/fixtures/scripts/convert-carcomido-sample.py
"""
import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
SAMPLE_PATH = REPO_ROOT / "galgoth_studio_build_pack/samples/carcomido_minecraft_cuboids.bbmodel"
OUTPUT_PATH = REPO_ROOT / "contracts/fixtures/carcomido-mob-project-model.json"
# Copia servida por el dev harness del viewport (ticket 008) -- Vite no
# puede importar/fetch-ear archivos fuera de frontend/ en runtime del
# navegador, así que se publica una copia idéntica en frontend/public/.
# SIEMPRE generada desde el mismo run que OUTPUT_PATH -- nunca editar una
# sin la otra a mano.
FRONTEND_PUBLIC_COPY_PATH = REPO_ROOT / "frontend/public/dev-fixtures/carcomido-mob-project-model.json"

FACE_NAMES = ["north", "south", "east", "west", "up", "down"]


def scalar_rotation_to_vec3(rotation, axis):
    """Los cube elements de este sample usan rotación escalar + un solo eje
    ('rotation': -7, 'axis': 'z') -- nunca un Vec3 de 3 ejes. Se mapea al
    eje correspondiente, 0 en los otros dos (ver CoordinateSystemContract,
    docs/adr/0001-coordinate-system-contract.md)."""
    vec = [0.0, 0.0, 0.0]
    index = {"x": 0, "y": 1, "z": 2}[axis or "z"]
    vec[index] = rotation or 0
    return vec


def convert_faces(bb_faces):
    return {
        face_name: {
            "uv": [float(v) for v in bb_faces[face_name]["uv"]],
            "texture": bb_faces[face_name]["texture"],
        }
        for face_name in FACE_NAMES
    }


def main():
    bbmodel = json.loads(SAMPLE_PATH.read_text())
    elements_by_uuid = {el["uuid"]: el for el in bbmodel["elements"]}

    bones = []
    cuboids = []
    referenced_uuids = set()

    def walk(node, parent_bone_id):
        if isinstance(node, dict):
            bone_id = node["uuid"]
            bones.append(
                {
                    "id": bone_id,
                    "name": node["name"],
                    "parentId": parent_bone_id,
                    "pivot": [float(v) for v in node["origin"]],
                    # Ningún group de este sample trae 'rotation' -> todos son 0 (ver Hecho del ticket 008).
                    "rotation": [float(v) for v in node.get("rotation", [0, 0, 0])],
                }
            )
            for child in node.get("children", []):
                walk(child, bone_id)
        else:
            # node es un string: uuid de un elemento (cuboid) hoja.
            referenced_uuids.add(node)
            element = elements_by_uuid[node]
            cuboids.append(
                {
                    "id": element["uuid"],
                    "name": element["name"],
                    "boneId": parent_bone_id,
                    "from": [float(v) for v in element["from"]],
                    "to": [float(v) for v in element["to"]],
                    "origin": [float(v) for v in element["origin"]],
                    "rotation": scalar_rotation_to_vec3(element.get("rotation", 0), element.get("axis")),
                    "faces": convert_faces(element["faces"]),
                }
            )

    for top_level in bbmodel["outliner"]:
        walk(top_level, None)

    orphaned = set(elements_by_uuid.keys()) - referenced_uuids
    if orphaned:
        orphan_names = [elements_by_uuid[u]["name"] for u in orphaned]
        print(
            f"AVISO: {len(orphaned)} elemento(s) del .bbmodel no están referenciados "
            f"por ningún group del outliner (huérfanos, invisibles también en Blockbench) "
            f"-- se EXCLUYEN del MobProjectModel convertido: {orphan_names}"
        )

    model = {
        "mobId": "dev-fixture-carcomido",
        "projectId": "dev-fixture-project",
        "name": bbmodel.get("name", "Carcomido"),
        "baseType": "humanoid",
        "units": "minecraft_pixels",
        "bones": bones,
        "cuboids": cuboids,
        "texture": {
            "width": bbmodel["resolution"]["width"],
            "height": bbmodel["resolution"]["height"],
            "storageKey": None,
        },
        # Este fixture es solo para renderizar geometría en el viewport
        # (ticket 008) -- no pasó por AutoUv (ticket 006/007), por eso
        # 'regions' queda vacío aunque cada cara ya trae la UV placeholder
        # original del .bbmodel (uv=[0,0,8,8], texture=null en TODAS las
        # caras -- el sample nunca fue pintado/mapeado a mano, ver Hecho).
        "uv": {
            "textureWidth": bbmodel["resolution"]["width"],
            "textureHeight": bbmodel["resolution"]["height"],
            "regions": [],
        },
        "animations": [],
        "exportSettings": {"preferredFormatVersion": "v4"},
        "referenceImages": [],
    }

    serialized = json.dumps(model, indent=2) + "\n"
    OUTPUT_PATH.write_text(serialized)
    FRONTEND_PUBLIC_COPY_PATH.parent.mkdir(parents=True, exist_ok=True)
    FRONTEND_PUBLIC_COPY_PATH.write_text(serialized)
    print(
        f"OK: {len(bones)} bones, {len(cuboids)} cuboids -> "
        f"{OUTPUT_PATH.relative_to(REPO_ROOT)} y {FRONTEND_PUBLIC_COPY_PATH.relative_to(REPO_ROOT)}"
    )


if __name__ == "__main__":
    main()
