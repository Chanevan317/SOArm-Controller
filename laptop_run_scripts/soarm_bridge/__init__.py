"""SOArm laptop bridge — phone intent to SO-100/SO-101 joint commands."""

MOTORS = (
    "shoulder_pan",
    "shoulder_lift",
    "elbow_flex",
    "wrist_flex",
    "wrist_roll",
    "gripper",
)

# Index into a joint vector by name.
J = {name: i for i, name in enumerate(MOTORS)}
