# Credits for the scene pictures

The scenes are in `app/src/main/assets/scenes/` (`d_<place>.webp`, with a blurred `-glass.jpg` for the clock's fill and
a `-mask.png` for the scenery in front of the digits). All thirteen were generated with Google Gemini for this
project, from prompts describing each real place at its time of day, and then cropped to 16:9 at 1920x1080. They
show real places but are not photographs of them.

| Scene | Place | Hours |
|---|---|---|
| Chandratal, Spiti | Chandratal lake, Himachal Pradesh, India | 12 am to 3 am |
| Pangong Lake | Pangong Tso, Ladakh, India | 3 am to 6 am |
| Munnar tea hills | Munnar, Kerala, India | 6 am to 9 am |
| Bachalpsee | Bachalpsee, Bernese Oberland, Switzerland | 6 am to 9 am |
| Lake Bled | Lake Bled, Slovenia | 9 am to 12 pm |
| Autumn boulevard | A city boulevard in autumn | 9 am to 12 pm |
| Maldives lagoon | A Maldives lagoon, half under water | 12 pm to 3 pm |
| Rakotz bridge | Rakotzbrücke, Kromlau, Germany | 12 pm to 3 pm |
| Jaisalmer | The Thar desert and Jaisalmer fort, Rajasthan, India | 3 pm to 6 pm |
| Lauterbrunnen | Lauterbrunnen valley, Switzerland | 3 pm to 6 pm |
| Worli | The Worli skyline and the Sea Link, Mumbai, India | 6 pm to 9 pm |
| Rainy New York | A Manhattan avenue in the rain | 6 pm to 9 pm |
| Varanasi ghats | The ghats of Varanasi, Uttar Pradesh, India | 9 pm to 12 am |

`tools/scene_add.py` turns any picture into a scene (the glass, the mask and the line for `SceneView`), and
`tools/scene_emu.sh` shows it on the emulator. Use pictures you have the right to share.

Fonts: Outfit and Instrument Serif, both under the SIL Open Font License 1.1.
