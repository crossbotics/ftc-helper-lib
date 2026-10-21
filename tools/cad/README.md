# Robot CAD tools

> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/crossbotics/ftc-helper-lib/issues).

`robot_cad.py` turns a STEP file of a whole robot into what the simulator needs: a parts list with seller part
numbers and spin axes (`read`), the clues for which motor drives which part (`clues`), and one STL model per moving
group (`groups`). The robot-file skill uses them: see `skills/robot-file/cad.md`.

They need Python 3.10 or newer with build123d (it brings the OpenCascade CAD library, about 500 MB):

```bash
python3 -m venv .venv-cad
.venv-cad/bin/pip install -r tools/cad/requirements.txt
.venv-cad/bin/python tools/cad/robot_cad.py
```

They are not part of the simulator's build or its tests: only teams that work from CAD need them. Seller CAD
belongs to the seller: keep STEP and STL files out of public repositories.
