"""Turns an .fbx of one animation into a .glb the game can read.

    ./gradlew :dungeon:convertAnimations

Run by Blender in the background -- there is no other tool in this repository
that reads FBX, and jME reads only glTF. Kept here rather than done by hand for
the reason the icon cutter is: a conversion nobody can repeat is a conversion
nobody dares change.

Three things happen on the way, and each is the answer to something that bit.

MESH GOES. The download carries the whole character -- body, cape, hat, eight
meshes of him -- because that is what was uploaded to be animated. None of it is
wanted: the game already has the mage, and what it is short of is the movement.
Dropping the meshes takes the file from 700 KB to a fraction of it, and leaves a
library of the shape this client likes best, which is bones and nothing else.

THE ACTION IS RENAMED. It arrives called `Rig_Medium|mixamo.com|Layer0`, which
is an exporter's bookkeeping rather than a name. The game asks its libraries for
clips BY NAME, so renaming it here is what lets the new file be an ordinary
library named in `AnimationsFrom` -- rather than needing a mechanism of its own
for files that hold exactly one thing.

THE BONES ARE ALREADY RIGHT. Worth writing down because it is luck rather than
design: this was animated on KayKit's own rig, so the 23 joints are named as
`mage.glb` names them and the client's retargeting matches them one for one.
An .fbx straight off Mixamo would arrive with `mixamorig:Hips` and friends and
would need every one of them renamed before any of this worked. The check below
fails loudly rather than exporting something that will animate nothing.
"""

import sys

import bpy

# The joints a KayKit character is built on. Checked rather than assumed: a clip
# whose bones are named differently retargets onto nothing at all, and what that
# looks like in the game is a mage who stands perfectly still while a meteor
# falls -- no error, no warning, nothing to search for.
KAYKIT_ROOT = "root"
KAYKIT_SOME = ("hips", "spine", "chest", "handslot.r", "handslot.l")


def convert(source, target, clip_name):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.fbx(filepath=source)

    armatures = [o for o in bpy.data.objects if o.type == "ARMATURE"]
    if len(armatures) != 1:
        raise SystemExit(f"{source}: {len(armatures)} armatures, expected exactly one")
    bones = {b.name for b in armatures[0].data.bones}
    missing = [name for name in (KAYKIT_ROOT,) + KAYKIT_SOME if name not in bones]
    if missing:
        raise SystemExit(
            f"{source}: not on the KayKit rig -- no {missing}. Its bones are "
            f"{sorted(bones)[:6]}... Rename them to KayKit's before converting, "
            f"or the clip will retarget onto nothing."
        )

    if len(bpy.data.actions) != 1:
        raise SystemExit(
            f"{source}: {len(bpy.data.actions)} animations, expected exactly one"
        )
    action = bpy.data.actions[0]
    first, last = action.frame_range
    action.name = clip_name

    for mesh in [o for o in bpy.data.objects if o.type == "MESH"]:
        bpy.data.objects.remove(mesh, do_unlink=True)

    # THE ARMATURE STANDS WHERE IT WAS PUT. The download's object carries a
    # rotation of its own -- (91.6, -3.9, -58.3) degrees here -- which is where
    # the character happened to be standing when it was uploaded rather than
    # anything about the movement. Cleared, so nothing but the pose is exported.
    #
    # It is NOT what lays him down in the game: that is the exporter's own Z-up
    # to Y-up conversion, which it writes onto the root as a quarter turn about
    # X whatever this object says, and which is dealt with on the other side --
    # see AnimationLibrary.facingIsTheGames.
    armature = armatures[0]
    armature.location = (0.0, 0.0, 0.0)
    armature.rotation_mode = "XYZ"
    armature.rotation_euler = (0.0, 0.0, 0.0)
    armature.scale = (1.0, 1.0, 1.0)

    # The exporter writes whatever the scene's frame range is, not the action's.
    scene = bpy.context.scene
    scene.frame_start = int(first)
    scene.frame_end = int(last)

    bpy.ops.export_scene.gltf(
        filepath=target,
        export_format="GLB",
        export_animations=True,
        export_animation_mode="ACTIONS",
        export_skins=True,
        # Nothing is drawn from this file, so everything a renderer would want is
        # weight. What the game takes is the tracks.
        export_materials="NONE",
        export_apply=False,
    )
    seconds = (last - first) / scene.render.fps
    print(f"CONVERTED {clip_name}  {int(last - first)} frames "
          f"at {scene.render.fps}fps = {seconds:.3f}s -> {target}")


if __name__ == "__main__":
    args = sys.argv[sys.argv.index("--") + 1:]
    if len(args) != 3:
        raise SystemExit("usage: blender -b -P fbx_to_glb.py -- <in.fbx> <out.glb> <clipName>")
    convert(args[0], args[1], args[2])
