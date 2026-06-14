"use client";

import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import { TextStyle } from '@tiptap/extension-text-style'
import { Color } from '@tiptap/extension-color'
import Image from '@tiptap/extension-image'
import { Bold, Italic, List, ListTodo, Undo, Redo, Image as ImageIcon } from 'lucide-react'

interface Props {
  content: string;
  onChange: (html: string) => void;
  textColor: string;
}

export default function RichTextEditor({ content, onChange, textColor }: Props) {
  const editor = useEditor({
    extensions: [
      StarterKit,
      TaskList,
      TaskItem.configure({ nested: true }),
      TextStyle,
      Color,
      Image,
    ],
    content,
    onUpdate: ({ editor }) => {
      onChange(editor.getHTML());
    },
    editorProps: {
      attributes: {
        class: 'prose prose-sm focus:outline-none min-h-[200px] w-full',
        style: `color: ${textColor}`
      }
    }
  });

  if (!editor) return null;

  const addImage = () => {
    const url = window.prompt('URL of the image:')
    if (url) {
      editor.chain().focus().setImage({ src: url }).run()
    }
  }

  return (
    <div className="flex flex-col border-0 bg-transparent h-full">
      <div className="flex items-center gap-1 p-2 border-b border-gray-200 dark:border-gray-800 flex-wrap">
        <button type="button" onClick={() => editor.chain().focus().toggleBold().run()} className={`p-1.5 rounded ${editor.isActive('bold') ? 'bg-gray-200 dark:bg-gray-700' : ''}`} style={{ color: textColor }}><Bold size={14} /></button>
        <button type="button" onClick={() => editor.chain().focus().toggleItalic().run()} className={`p-1.5 rounded ${editor.isActive('italic') ? 'bg-gray-200 dark:bg-gray-700' : ''}`} style={{ color: textColor }}><Italic size={14} /></button>
        <div className="w-px h-4 bg-gray-300 dark:bg-gray-700 mx-1" />
        <button type="button" onClick={() => editor.chain().focus().toggleBulletList().run()} className={`p-1.5 rounded ${editor.isActive('bulletList') ? 'bg-gray-200 dark:bg-gray-700' : ''}`} style={{ color: textColor }}><List size={14} /></button>
        <button type="button" onClick={() => editor.chain().focus().toggleTaskList().run()} className={`p-1.5 rounded ${editor.isActive('taskList') ? 'bg-gray-200 dark:bg-gray-700' : ''}`} style={{ color: textColor }}><ListTodo size={14} /></button>
        <button type="button" onClick={addImage} className="p-1.5 rounded" style={{ color: textColor }}><ImageIcon size={14} /></button>
        <div className="w-px h-4 bg-gray-300 dark:bg-gray-700 mx-1" />
        <button type="button" onClick={() => editor.chain().focus().undo().run()} disabled={!editor.can().undo()} className="p-1.5 rounded disabled:opacity-50" style={{ color: textColor }}><Undo size={14} /></button>
        <button type="button" onClick={() => editor.chain().focus().redo().run()} disabled={!editor.can().redo()} className="p-1.5 rounded disabled:opacity-50" style={{ color: textColor }}><Redo size={14} /></button>
        <div className="ml-auto flex items-center gap-1">
           <input type="color" onInput={e => editor.chain().focus().setColor((e.target as HTMLInputElement).value).run()} value={editor.getAttributes('textStyle').color || textColor} className="w-6 h-6 p-0 border-0 rounded cursor-pointer" />
        </div>
      </div>
      <div className="p-3 flex-1 overflow-y-auto">
        <EditorContent editor={editor} />
      </div>
    </div>
  );
}
